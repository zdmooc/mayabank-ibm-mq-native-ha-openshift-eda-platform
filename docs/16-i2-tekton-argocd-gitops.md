# I2 — Tekton, Argo CD et GitOps

## Objectif

Industrialiser le POC sans confondre CI et CD :

- **Tekton** : clone, tests Maven, scan source/misconfiguration, build OpenShift ;
- **Argo CD** : déploiement déclaratif, détection de dérive, self-heal et prune ;
- secrets hors Git ;
- promotion d'image immuable à ajouter après validation du pipeline.

## Statut

**RUNTIME_VALIDATED sur CRC le 2026-09-15.**

Preuves principales :

- `PipelineRun payments-ci-7xxc2` : `Succeeded` ;
- clone, tests, Trivy et OpenShift Build : `exit=0` ;
- tests Maven : `12` exécutés, `0` échec, `0` erreur ;
- `Build payments-12` : `Complete` ;
- image produite : `payments@sha256:b310a6ec38360f0497bbde53d3fa5e7873bef334810d7591754acd5c9f26e629` ;
- Applications Argo CD `mayabank-mq-local-core` et `mayabank-mq-local-db` : `Synced / Healthy` ;
- drift contrôlé sur `payment-processing` de `replicas=1` vers `replicas=0`, puis restauration automatique à `replicas=1`, `ready=1`, `Synced / Healthy` ;
- namespace `mayabank-mq-local` délégué à OpenShift GitOps via `argocd.argoproj.io/managed-by=openshift-gitops`.

Les sorties détaillées sont enregistrées sous `evidence/i2-20260915/`.

## Tekton

Manifeste permanent : `deploy/tekton/payments-pipeline.yaml`.
Run de lab : `deploy/tekton/payments-pipelinerun.yaml`.

Le pipeline comporte un `Task` autonome :

1. clone du dépôt et de la révision demandée ;
2. `mvn test` ;
3. scan Trivy HIGH/CRITICAL sur vulnérabilités, secrets et misconfigurations ;
4. déclenchement du `BuildConfig/payments` existant avec `oc start-build --from-dir`.

Le compte de service obtient uniquement les droits de lecture nécessaires et les sous-ressources OpenShift `buildconfigs/instantiate*` nécessaires au binary build.

### Exécution lab

Pré-requis : OpenShift Pipelines/Tekton installé.

```bash
oc apply -f deploy/payments/build.yaml
oc apply -f deploy/tekton/payments-pipeline.yaml
oc create -f deploy/tekton/payments-pipelinerun.yaml
```

Pour une nouvelle exécution, recréer seulement un nouveau `PipelineRun` ; ne pas réappliquer les objets permanents sans raison.

Vérifier :

```bash
oc -n mayabank-mq-build get pipelinerun,taskrun,pods
oc -n mayabank-mq-build get builds
oc -n mayabank-mq-build get istag payments:0.2.0
```

### Particularités OpenShift validées

Les steps Tekton travaillent à la racine du workspace puis effectuent les `cd` dans leurs scripts. Cela évite les problèmes de permissions provoqués par des sous-répertoires de `workingDir` précréés par Tekton sous SCC OpenShift. `HOME=/tekton/home` est également utilisé pour éviter les écritures dans `/`.

Le gate Trivy reste bloquant sur HIGH/CRITICAL pour vulnérabilités et secrets. Le scan de misconfiguration Kubernetes est strict sur les manifests applicatifs et exclut explicitement certains manifests locaux CRC lorsque l'image vendor impose un filesystem writable. Cette exclusion ne doit pas être transposée implicitement aux manifests de production.

## Argo CD

Manifeste : `deploy/argocd/applications.yaml`.

Deux Applications séparent :

- le socle MQ local + NetworkPolicies + processeur ;
- PostgreSQL/idempotence.

Les Jobs de test ne sont pas gérés en continu par Argo CD. Ils restent des sondes déclenchées explicitement.

Les secrets `mq-app-credentials` et `payments-db-credentials` ne sont pas dans Git. Ils doivent exister avant le premier sync. Une cible Vault/External Secrets est fournie séparément comme modèle et ne doit être activée que si l'opérateur correspondant est installé.

### Exécution lab

Pré-requis : OpenShift GitOps installé et secrets déjà présents.

Le namespace cible doit être délégué à l'instance OpenShift GitOps :

```bash
oc label namespace mayabank-mq-local \
  argocd.argoproj.io/managed-by=openshift-gitops \
  --overwrite
```

Puis :

```bash
oc apply -f deploy/argocd/applications.yaml
oc -n openshift-gitops get applications.argoproj.io
```

Après merge de la branche, les Applications suivent `main`. Pour tester avant merge, appliquer localement le manifeste avec `targetRevision` remplacé temporairement par `feature/covea-eda-i1-i4`, sans committer cette substitution.

### Test de dérive validé

Le test exécuté sur CRC a forcé :

```text
payment-processing replicas: 1 -> 0
```

Argo CD a ensuite restauré automatiquement :

```text
replicas=1 ready=1 sync=Synced health=Healthy
SELF_HEAL_OK
```

Ce test valide la détection de dérive et le self-heal sur une ressource applicative gérée.

## Promotion d'image

Le pipeline construit l'image ; Argo CD déploie l'état Git. Le run validé identifie bien l'image par digest, mais `processor.yaml` référence encore le tag `payments:0.2.0` pour le profil CRC.

La cible production ne doit pas utiliser un tag mutable comme preuve de promotion. Le prochain durcissement consiste à :

1. lire le digest produit par l'ImageStream ;
2. mettre à jour le manifeste de déploiement avec `@sha256:...` ;
3. faire relire/merger ce changement ;
4. laisser Argo CD promouvoir exactement ce digest.

Cela sépare correctement **build** et **release**.

## Critère de clôture I2

I2 est `RUNTIME_VALIDATED` car :

- un PipelineRun passe avec tests + scan + build ;
- l'image produite est identifiée par digest ;
- Argo CD synchronise les deux Applications ;
- un test de drift/self-heal est prouvé ;
- aucun secret n'est exposé dans Git ou dans les logs de preuve.

La promotion immuable par digest reste un durcissement de release, pas un blocage de la validation I2 du POC CRC.

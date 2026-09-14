# I2 — Tekton, Argo CD et GitOps

## Objectif

Industrialiser le POC sans confondre CI et CD :

- **Tekton** : clone, tests Maven, scan source/misconfiguration, build OpenShift ;
- **Argo CD** : déploiement déclaratif, détection de dérive, self-heal et prune ;
- secrets hors Git ;
- promotion d'image immuable à ajouter après validation du pipeline.

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

> Statut : `IMPLEMENTED / READY_TO_RUN`. Aucun run Tekton n'est revendiqué tant qu'une sortie cluster n'est pas enregistrée.

## Argo CD

Manifeste : `deploy/argocd/applications.yaml`.

Deux Applications séparent :

- le socle MQ local + NetworkPolicies + processeur ;
- PostgreSQL/idempotence.

Les Jobs de test ne sont pas gérés en continu par Argo CD. Ils restent des sondes déclenchées explicitement.

Les secrets `mq-app-credentials` et `payments-db-credentials` ne sont pas dans Git. Ils doivent exister avant le premier sync. Une cible Vault/External Secrets est fournie séparément comme modèle et ne doit être activée que si l'opérateur correspondant est installé.

### Exécution lab

Pré-requis : OpenShift GitOps installé et secrets déjà présents.

```bash
oc apply -f deploy/argocd/applications.yaml
oc -n openshift-gitops get applications.argoproj.io
```

Après merge de la branche, les Applications suivent `main`. Pour tester avant merge, modifier temporairement `targetRevision` vers `feature/covea-eda-i1-i4` puis revenir à `main`.

### Test de dérive

Après un premier sync vert, modifier volontairement un champ non critique d'une ressource gérée puis observer Argo CD restaurer l'état Git. Conserver l'événement et le statut de sync dans `evidence/`.

## Promotion d'image

Le pipeline construit l'image ; Argo CD déploie l'état Git. La cible production ne doit pas utiliser un tag mutable comme preuve de promotion. Le prochain durcissement consiste à :

1. lire le digest produit par l'ImageStream ;
2. mettre à jour le manifeste de déploiement avec `@sha256:...` ;
3. faire relire/merger ce changement ;
4. laisser Argo CD promouvoir exactement ce digest.

Cela sépare correctement **build** et **release**.

## Critère de clôture I2

I2 devient `RUNTIME_VALIDATED` lorsque :

- un PipelineRun passe avec tests + scan + build ;
- l'image produite est identifiée par digest ;
- Argo CD synchronise les deux Applications ;
- un test de drift/self-heal est prouvé ;
- aucun secret n'est exposé dans Git ou dans les logs de preuve.

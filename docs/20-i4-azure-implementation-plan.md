# I4 — Plan d'implémentation Azure

## Statut

**PLAN_READY — EXECUTION NOT STARTED**

Ce plan transforme le design Azure ARO en séquence exécutable. Il ne doit être lancé qu'après validation budget/licence/quotas.

## A0 — Cadrage Azure

Livrables :

- abonnement et Resource Groups retenus ;
- région primaire avec Availability Zones ;
- estimation de coût du lab ;
- quotas ARO/compute validés ;
- version ARO/OpenShift candidate ;
- matrice de compatibilité IBM MQ Operator vérifiée ;
- entitlement IBM MQ confirmé ;
- décision sur la durée de vie du lab.

Gate : aucune création cloud avant accord explicite.

## A1 — Landing zone / réseau

Créer par IaC :

- VNet/spoke ARO ;
- subnets requis ;
- DNS privé ;
- connectivité hub ;
- egress contrôlé ;
- tags/budget/policies ;
- Key Vault.

Preuves : plan IaC, diff, ressources créées, coût estimé.

## A2 — Cluster ARO

Créer le cluster ARO privé avec :

- pull secret Red Hat hors Git ;
- managed identity lorsque retenue ;
- au moins trois workers ;
- capacité répartie sur trois zones ;
- accès admin contrôlé.

Vérifications :

```bash
oc get nodes -L topology.kubernetes.io/zone,kubernetes.io/hostname
oc get sc
oc get clusterversion
```

## A3 — Pool workers MQ et stockage

Objectif : garantir la capacité Native HA.

- vérifier/ajouter MachineSets MQ par zone si nécessaire ;
- labels workers MQ ;
- vérifier anti-affinité/topology spread supportée par IBM MQ Operator ;
- sélectionner Azure Disk CSI RWO ;
- vérifier `volumeBindingMode` et zones ;
- lancer un test PVC par zone ;
- mesurer latence/IOPS de base.

Gate : ne pas déployer MQ si trois workers/volumes indépendants ne peuvent pas être garantis.

## A4 — IBM MQ Operator, PKI et GitOps

- installer IBM MQ Operator avec version épinglée ;
- créer `mayabank-mq-prod` ;
- configurer Argo CD ;
- connecter Key Vault au mécanisme de secrets retenu ;
- générer/synchroniser les secrets TLS hors Git ;
- valider `qm-prod-security` ;
- créer l'identité de monitoring `mqmonitor` ;
- conserver `license.accept=false` jusqu'à validation finale.

## A5 — Déploiement Native HA

Après validation licence :

- appliquer la configuration sécurité ;
- déployer le `QueueManager` Native HA ;
- attendre les trois pods ;
- vérifier placement node/zone ;
- vérifier les trois PVC ;
- vérifier `dspmq -o nativeha -x` ;
- valider Active/Replica/INSYNC/quorum.

Dossier de preuve : `evidence/i4-azure-YYYYMMDD/`.

## A6 — Applications et sécurité

- déployer PostgreSQL de lab ou service DB retenu ;
- déployer payment-processing ;
- exécuter payment-order ;
- valider mTLS ;
- valider CHLAUTH ;
- valider OAM positif/négatif ;
- valider idempotence ;
- valider retry/backout/DLQ/replay.

Aucun compte générique `app` dans la cible.

## A7 — Observabilité

- UWM activé ;
- ServiceMonitor MQ ;
- exporter queue-level dédié si requis ;
- identité `mqmonitor` ;
- dashboard Grafana ;
- PrometheusRules ;
- alertes testées.

Minimum dashboard :

- QM status ;
- Native HA status ;
- queue depth ;
- oldest message age ;
- DLQ ;
- BACKOUT ;
- CPU/RAM ;
- PVC/storage ;
- scrape target.

## A8 — Résilience

Exécuter dans cet ordre :

1. baseline nominal ;
2. perte pod actif ;
3. récupération 3/3 ;
4. perte worker actif ;
5. récupération 3/3 ;
6. scénario zone si faisable ;
7. test quorum destructif uniquement avec autorisation spécifique.

Pour chaque test : T0, rôle Native HA avant/après, logs JMS, paiements, DB idempotence, métriques, temps de reprise.

## A9 — Clôture et destruction du lab

- collecter les preuves ;
- exporter coûts ;
- mettre à jour README/docs/PR ;
- conserver uniquement les artefacts non sensibles ;
- détruire ressources Azure du lab selon plan approuvé ;
- vérifier absence de ressources orphelines coûteuses.

## Matrice Done

| Domaine | Design | Implémentation Azure | Runtime |
|---|---|---|---|
| ARO multi-AZ | DONE | PENDING | PENDING |
| IBM MQ Operator | DONE | PENDING | PENDING |
| Native HA 3 instances | DONE | PENDING | PENDING |
| Azure Disk RWO x3 | DONE | PENDING | PENDING |
| mTLS/CHLAUTH/OAM | DONE | PENDING | PENDING |
| Key Vault/secrets | DONE | PENDING | PENDING |
| GitOps | DONE | PENDING | PENDING |
| Observabilité | DONE | PENDING | PENDING |
| Failover pod | DONE | PENDING | PENDING |
| Failover worker | DONE | PENDING | PENDING |
| HA multi-AZ | DONE | PENDING | PENDING |
| PRA inter-région | STUDY | NOT IN I4 | NOT IN I4 |

## Formulation CV tant que le lab Azure n'est pas exécuté

> Conception d'une architecture IBM MQ Native HA sur OpenShift/ARO : quorum 3 instances, réplication TLS, stockage RWO par instance, mTLS/CHLAUTH/OAM, GitOps, observabilité et runbooks de failover ; validation runtime multi-worker Azure planifiée.

Ne pas écrire `Native HA testé sur Azure` avant les preuves A5-A8.

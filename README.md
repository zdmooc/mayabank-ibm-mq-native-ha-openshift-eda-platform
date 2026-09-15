# MayaBank — IBM MQ Native HA / OpenShift / EDA

POC personnel de modernisation du messaging bancaire : partir des labs WAS/JMS/MDB existants, construire un parcours paiement reproductible sur OpenShift Local, puis valider IBM MQ Native HA sur un cluster OpenShift multi-nœuds.

**État runtime prouvé sur CRC : I1 fiabilité métier et I2 CI/CD GitOps sont `RUNTIME_VALIDATED`.** MQ mono-instance, persistance, paiement JMS authentifié, reprise après redémarrage, retry/backout, DLQ applicative, rejeu contrôlé, idempotence PostgreSQL avec crash après commit DB/avant commit transaction JMS-MQ, pipeline Tekton et self-heal Argo CD disposent de preuves runtime. Native HA multi-worker n'est toujours pas revendiqué comme exécuté.

## Parcours de finalisation I1 -> I4

La branche `feature/covea-eda-i1-i4` porte la préparation de la cible entretien/production. Elle distingue strictement **IMPLEMENTED** de **RUNTIME_VALIDATED**.

| Itération | Livré dans le dépôt | Statut |
|---|---|---|
| I1 — fiabilité métier | idempotence PostgreSQL, crash DB/MQ, redelivery, readiness liée à la connexion MQ | **RUNTIME_VALIDATED sur CRC — 2026-09-15** |
| I2 — industrialisation | Tekton build/test/Trivy/OpenShift Build + Argo CD GitOps/self-heal | **RUNTIME_VALIDATED sur CRC — 2026-09-15** |
| I3 — sécurité/observabilité | client JMS mTLS/reconnect, CHLAUTH/OAM least privilege, PrometheusRule, dashboard Grafana, runbooks | DESIGNED + IMPLEMENTED / validation runtime partielle sur CRC |
| I4 — Native HA | QueueManager Operator NativeHA 3 instances, TLS réplication, stockage persistant, sécurité, observabilité, runbooks, design Azure ARO multi-AZ | **DESIGN_COMPLETE / AZURE IMPLEMENTATION PENDING / RUNTIME PENDING** |

Guides :

- [I1 — fiabilité métier et idempotence](docs/15-i1-fiabilite-metier.md)
- [I2 — Tekton et Argo CD](docs/16-i2-tekton-argocd-gitops.md)
- [I3 — sécurité, observabilité et exploitation](docs/17-i3-securite-observabilite.md)
- [I4 — IBM MQ Native HA multi-worker](docs/18-i4-native-ha.md)
- [I4 — design cible Azure Red Hat OpenShift](docs/19-i4-azure-aro-target-design.md)
- [I4 — plan d'implémentation Azure](docs/20-i4-azure-implementation-plan.md)

**Règle de vérité :** un manifeste, un script ou un pipeline livré n'est pas une preuve d'exécution. Le CV doit continuer à distinguer `DESIGNED / IMPLEMENTED / READY_TO_RUN / RUNTIME_VALIDATED`.

## Deux profils

| Profil | But | Limite |
|---|---|---|
| local-crc | MQ mono-instance, paiement, erreurs, sécurité, CI/CD et supervision | CRC mono-nœud ne démontre pas la survie à une panne de nœud |
| azure-aro-native-ha | Trois instances MQ sur trois workers, cible trois Availability Zones, Azure Disk RWO, Key Vault, GitOps et tests de failover | implémentation Azure, licences, quotas et coût à valider avant déploiement |

## Parcours paiement Java/JMS

[Guide de déploiement paiement](docs/08-paiement-jms.md) : Job payment-order, service payment-processing, secret MQ et test de refus du mauvais mot de passe. Exécution CRC du parcours valide réussie. [Preuves JMS et reprise](evidence/2026-09-10-crc-jms-request-reply.md) ; [preuves MQ initiales](evidence/2026-09-09-crc-mq.md).

Installation du lot : `bash scripts/payments/deploy.sh`. Pour reprendre une installation déjà validée, suivre le [point de pause](docs/10-reprise-et-reste-a-faire.md), puis `bash scripts/payments/verify.sh`, sans reconstruction inutile.

## Fiabilité déjà couverte

- Retry/backout : [preuve CRC](evidence/2026-09-11-crc-retry-backout.md).
- DLQ applicative : [preuve CRC](evidence/2026-09-11-crc-dlq-applicative.md).
- Rejeu contrôlé : [preuve CRC](evidence/2026-09-11-crc-rejeu-controle.md).
- Idempotence durable PostgreSQL : `RUNTIME_VALIDATED` sur CRC avec scénarios NEW, DUPLICATE, CONFLICT et crash contrôlé après commit PostgreSQL / avant commit transaction JMS-MQ ; preuves sous `evidence/i1-20260915/`.
- CI/CD GitOps : `RUNTIME_VALIDATED` avec `PipelineRun payments-ci-7xxc2`, 12 tests Maven, gate Trivy, `Build payments-12`, digest d'image et self-heal Argo CD ; preuves sous `evidence/i2-20260915/`.

## Cible IBM MQ Native HA

`deploy/native-ha/qm-prod.yaml` prépare un `QueueManager` `mq.ibm.com/v1beta1` en Native HA avec :

- IBM MQ 9.4.5.1-r1 comme version candidate à revalider avant Azure ;
- trois instances gérées par IBM MQ Operator ;
- chiffrement du trafic de réplication ;
- stockage persistant RWO ;
- configuration mTLS/CHLAUTH/OAM ;
- métriques Prometheus et ServiceMonitor ;
- console web désactivée.

Le design Azure ARO est désormais complet : cluster privé multi-worker, cible trois zones, Azure Disk RWO indépendant par instance, Key Vault pour les secrets, réseau hub/spoke, GitOps, observabilité et matrice de tests pod/worker/zone. Aucun de ces éléments Azure n'est présenté comme exécuté tant que le lab n'existe pas.

La licence reste volontairement `accept: false` dans Git. Aucun déploiement Native HA ne doit être lancé sans validation explicite de l'entitlement, du coût et du cluster.

## Commencer / reprendre

1. Lire le [cadrage et l'architecture](docs/01-architecture.md).
2. Collecter les [prérequis CRC](docs/02-prerequis-crc.md), sans modifier le cluster.
3. Finaliser les derniers points d'observabilité I3 sur CRC avec preuves.
4. Pour I4, utiliser le [design ARO](docs/19-i4-azure-aro-target-design.md), puis le [plan Azure](docs/20-i4-azure-implementation-plan.md).
5. Enregistrer chaque résultat Azure dans `evidence/i4-azure-YYYYMMDD/` avec date, commit et sorties.

## Périmètre cible

- Applications Java payment-order et payment-processing ; PostgreSQL pour la persistance métier.
- IBM MQ : messages persistants, transactions, DLQ, backout, retry borné, rejeu contrôlé et idempotence.
- OpenShift/ARO : stockage, RBAC, quotas, NetworkPolicies, multi-AZ, exposition adaptée aux protocoles.
- Argo CD, IBM MQ Operator, Tekton, IaC Azure, gestion externalisée des secrets.
- Prometheus/Grafana : métriques MQ, alertes, dashboards ; corrélation applicative.
- Tests de résilience, capacité, documentation HLD/LLD, ADR et runbooks.
- Extensions après validation du socle : PRA/CRR, pont MQ vers Kafka, comparaison AMQ Broker, couche API sécurisée.

## Règles

Pas de mot de passe, clé privée, kubeconfig, pull secret ou état Terraform dans Git.
Pas de création cloud sans validation du coût et autorisation explicite.
Pas de mention « testé » sans résultat enregistré. Un script écrit n'est pas une preuve d'exécution.
Le profil Native HA reste `runtime pending` tant que ses essais multi-worker Azure ne sont pas exécutés.
Les versions doivent être épinglées après vérification de compatibilité ; aucune dépendance `latest` pour une preuve finale.

## Sources de départ

- [formation-was](https://github.com/zdmooc/formation-was)
- [assessment-was-openshift](https://github.com/zdmooc/assessment-was-openshift)
- [CRC](https://crc.dev/docs/getting-started/)
- [Azure Red Hat OpenShift — service definition](https://learn.microsoft.com/azure/openshift/openshift-service-definitions)
- [IBM MQ : licences](https://www.ibm.com/docs/en/ibm-mq/9.4.x?topic=mq-license-information)
- [IBM MQ Native HA](https://www.ibm.com/docs/en/ibm-mq/9.4.x?topic=availability-native-ha)
- [IBM MQ Operator — QueueManager API](https://www.ibm.com/docs/en/ibm-mq/9.4.x?topic=operator-api-reference-queuemanager-mqibmcomv1beta1)

Les liens versionnés sont des références de départ, pas une preuve de compatibilité de l'environnement local ou Azure.

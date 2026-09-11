# MayaBank — IBM MQ Native HA / OpenShift / EDA

POC personnel de modernisation du messaging bancaire : partir des labs WAS/JMS/MDB existants, construire un parcours paiement reproductible sur OpenShift Local, puis valider IBM MQ Native HA sur un cluster OpenShift multi-nœuds GCP.

**État au 11 septembre 2026 : MQ mono-instance, persistance, paiement JMS authentifié et reprise après redémarrage validés sur CRC par sorties utilisateur.** Contrôle automatisé HTP validé sur la configuration déjà corrigée. Installation sur volume neuf et Native HA non validées ; aucun module Terraform livré.

**Reprendre ici : [point de pause et checklist complète](docs/10-reprise-et-reste-a-faire.md).** Retry/backout et paiement valide après rejet : [test CRC réussi](evidence/2026-09-11-crc-retry-backout.md). DLQ applicative : [test CRC réussi](evidence/2026-09-11-crc-dlq-applicative.md). Rejeu contrôlé : [test CRC réussi](evidence/2026-09-11-crc-rejeu-controle.md). Prochain lot : idempotence durable. Le routage automatique en DLQ par canal reste non testé.

## Deux profils

| Profil | But | Limite |
|---|---|---|
| local-crc | MQ mono-instance, paiement, erreurs, sécurité, CI/CD et supervision | CRC mono-nœud ne démontre pas la survie à une panne de nœud |
| gcp-native-ha | Trois instances MQ sur trois workers distincts ; tests de panne et reconnexion | Accès, versions, licences, stockage et budget à valider avant déploiement |

## Parcours paiement Java/JMS — nouveau lot

[Guide de déploiement paiement](docs/08-paiement-jms.md) : Job payment-order, service payment-processing, secret MQ et test de refus du mauvais mot de passe. Exécution CRC du parcours valide réussie. [Preuves JMS et reprise](evidence/2026-09-10-crc-jms-request-reply.md) ; [preuves MQ initiales](evidence/2026-09-09-crc-mq.md).

Installation du lot : `bash scripts/payments/deploy.sh`. Pour reprendre une installation déjà validée, suivre le point de pause ci-dessus : contrôle HTP puis `bash scripts/payments/verify.sh`, sans reconstruction inutile. Ne pas réappliquer le profil initial après activation du lot paiement.

## Essai local initial

Consulter le [guide MQ local](docs/07-deploiement-local.md), puis lancer les scripts depuis le clone. Image Developer 9.4.5.1-r1 ; sans opérateur pour ce premier essai. Support officiel opérateur/OpenShift 4.22.7 non confirmé. Aucun test Native HA exécuté.

## Commencer

1. Lire le [cadrage et l'architecture](docs/01-architecture.md).
2. Collecter les [prérequis CRC](docs/02-prerequis-crc.md), sans modifier le cluster.
3. Suivre les [itérations et critères de validation](docs/03-iterations.md).
4. Consulter la [réutilisation des labs](docs/04-reutilisation.md).
5. Appliquer les règles [FinOps et accès GCP](docs/05-finops-gcp.md).
6. Enregistrer les résultats dans la [matrice de preuves](docs/06-preuves.md).

## Périmètre cible

- Applications Java payment-order et payment-processing ; PostgreSQL pour la persistance métier.
- IBM MQ : messages persistants, transactions, DLQ, backout, retry borné, rejeu contrôlé et idempotence.
- OpenShift : stockage, RBAC, quotas, NetworkPolicies, exposition adaptée aux protocoles.
- Argo CD, opérateur IBM MQ, Tekton, Terraform, Ansible, Vault.
- Prometheus/Grafana : métriques MQ, alertes, dashboards ; corrélation applicative.
- Tests de résilience, capacité, documentation HLD/LLD, ADR et runbooks.
- Extensions après validation du socle : pont MQ vers Kafka, comparaison AMQ Broker, couche API sécurisée.
- Anti-fraude : extension facultative, pas un prérequis au parcours de paiement initial.

## Règles

Pas de mot de passe, clé privée, kubeconfig, pull secret ou état Terraform dans Git.
Pas de création cloud sans validation du coût et autorisation explicite.
Pas de mention « testé » sans résultat enregistré. Un script écrit n'est pas une preuve d'exécution.
Le profil gcp-native-ha reste prévu tant que ses essais ne sont pas exécutés.
Les versions seront épinglées après vérification de compatibilité ; aucune dépendance latest.

## Sources de départ

- [formation-was](https://github.com/zdmooc/formation-was)
- [assessment-was-openshift](https://github.com/zdmooc/assessment-was-openshift)
- [CRC](https://crc.dev/docs/getting-started/)
- [IBM MQ : licences](https://www.ibm.com/docs/en/ibm-mq/9.4.x?topic=mq-license-information)
- [OpenShift : installation sur GCP](https://docs.redhat.com/en/documentation/openshift_container_platform/4.20/html/installing_on_google_cloud/installing-gcp-customizations)

Les liens versionnés sont des références de départ, pas une sélection de version cible.

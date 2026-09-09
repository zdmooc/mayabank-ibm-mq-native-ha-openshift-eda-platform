# Cadrage et architecture cible — lot 01

## Objectif métier

Une banque fictive reçoit une instruction de paiement. Elle lui attribue un identifiant métier stable, la publie dans MQ, puis un consommateur enregistre le traitement et expose son statut. Aucune connexion bancaire réelle, donnée client ou mouvement d'argent réel.

## Flux logique

Client → API payment-order → PAYMENT.REQUEST.Q → payment-processing → PostgreSQL.
Un endpoint de consultation expose le statut de traitement. Le statut ACCEPTED doit être distingué de PROCESSED : acceptation technique ne signifie pas exécution du paiement.

Messages proposés : eventId, paymentId, schemaVersion, correlationId, timestamp, amount, currency. Validation des montants et schéma ; données de comptes fictives uniquement.

La confirmation API après publication, les fenêtres de panne et la stratégie outbox éventuelle font l'objet d'un ADR avant implémentation.

## Transactions et fiabilité

- Comparer portage Liberty/JMS/MDB et refactoring en services autonomes avant de choisir le runtime.
- Ne pas recopier le mélange NOT_SUPPORTED/autocommit du lab phase 05 comme garantie atomique MQ/DB.
- Choisir explicitement XA avec ressources compatibles et récupération testée, ou traitement au moins une fois avec inbox/idempotence durable.
- Une clé unique seule n'est pas toute la stratégie : gérer le doublon comme un cas normal, avec concurrence et crash après commit DB avant acquittement MQ.
- Distinguer erreurs transitoires, messages invalides et erreurs métier.
- DLQ et backout queue ont des rôles différents. BOQNAME/BOTHRESH ne constituent pas, seuls, la preuve du déplacement : vérifier le comportement du client JMS/resource adapter.
- Un retry est borné ; un rejeu est autorisé, tracé et contrôlé.
- Aucune promesse générale de exactly-once.

## Profil local-crc

Un MQ mono-instance avec volume persistant, les applications et PostgreSQL de laboratoire. Installation progressive des autres composants selon la RAM réellement disponible.
La suppression d'un pod teste un redémarrage/recovery, pas un failover Native HA.
La panne du PC ou du nœud interrompt tout le laboratoire.

## Profil gcp-native-ha

Cible de cadrage : trois nœuds de contrôle et trois workers ; chaque instance du même queue manager Native HA est placée sur un worker distinct.
Ce sont trois instances d'un queue manager logique, pas trois brokers actifs pour augmenter le débit.
Prévoir anti-affinité/topologie, volumes distincts, StorageClass compatible, ressources suffisantes et capacité de resynchronisation.
Répartition multi-zone, latence de réplication et performances disque à qualifier.
Une VM bootstrap temporaire et les composants réseau nécessaires sont à intégrer au devis final.

HA locale au cluster ≠ PRA inter-région. Aucun PRA complet n'est revendiqué au lot 01.

## Responsabilités d'automatisation

| Outil | Responsabilité cible |
|---|---|
| Terraform | Ressources GCP explicitement attribuées à son état |
| Installateur OpenShift | Ressources du cluster selon méthode IPI/UPI à décider |
| Argo CD | Configuration déclarative des workloads et de la plateforme |
| Opérateur IBM MQ | Cycle de vie du queue manager selon compatibilité/licence |
| Tekton | Build, tests, scan et publication d'images |
| Ansible | Opérations Day 2 cadrées, sans lutter contre la réconciliation GitOps |
| Vault | Source de secrets ; External Secrets peut assurer la synchronisation |

Aucune ressource ne doit être possédée simultanément par Terraform et l'installateur. Bootstrap GitOps, état distant et destruction suivent ce partage de propriété.

## Sécurité et observabilité

MQ : TLS/mTLS, authentification, CHLAUTH, autorisations minimales, rotation des certificats.
API : OAuth2/JWT, contrôles d'accès, quotas/rate limiting au niveau de la gateway.
Ne pas assimiler une Route HTTP à une exposition MQ TCP ; décider le mode réseau compatible avec MQ/TLS.
Logs sans secrets ni données sensibles ; corrélation par identifiants fictifs.
Métriques : profondeur, ancienneté selon métriques disponibles, débit, erreurs, canaux, stockage, latence applicative.
Prévoir alertes et runbooks associés, pas seulement un dashboard.

## ADR à produire avant les lots concernés

1. Runtime applicatif et réutilisation phase 13.
2. Atomicité, idempotence et acquittement.
3. Versions MQ/OpenShift/operator/Java et droits d'usage.
4. Stockage et topologie Native HA.
5. Propriété Terraform/installateur et cycle de destruction.
6. Secrets, PKI et exposition réseau.
7. SLI/SLO, RTO observé et protocole de mesure.

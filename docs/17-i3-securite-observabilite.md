# I3 — Sécurité, observabilité et exploitation

## Objectif

Passer d'un POC fonctionnel à un socle présentable comme cible de production : mTLS, identités séparées, autorisations minimales, secrets hors Git, métriques MQ, alertes, dashboard et runbooks.

## Sécurité MQ

Le fichier `deploy/security/qm-prod-security.yaml` définit la cible :

- deux channels SVRCONN distincts : `PAYMENT.ORDER.SVRCONN` et `PAYMENT.PROCESSING.SVRCONN` ;
- `SSLCAUTH(REQUIRED)` et TLS 1.3 ;
- mapping certificat -> identité MQ avec `CHLAUTH` ;
- `paymentorder` : PUT sur REQUEST, GET/BROWSE sur RESPONSE ;
- `paymentproc` : GET/BROWSE sur REQUEST, PUT sur RESPONSE/BACKOUT ;
- aucun accès applicatif direct à la DLQ pour éviter le rejeu sauvage.

Les certificats et clés privées ne sont jamais versionnés. Le manifeste Native HA I4 référence des Secrets Kubernetes à créer depuis une PKI de lab ou, en cible, depuis Vault/External Secrets/cert-manager selon la plateforme.

### Tests négatifs attendus

1. certificat absent -> connexion refusée ;
2. certificat signé par une CA non approuvée -> refus ;
3. certificat `payment-order` sur le channel du processeur -> refus ou droits insuffisants ;
4. `payment-order` tente un GET sur REQUEST -> refus OAM ;
5. certificat expiré/révoqué -> refus selon la PKI mise en place.

Conserver les reason codes MQ et les événements côté queue manager sans publier les secrets.

## Observabilité

Le QueueManager I4 active l'endpoint Prometheus de l'Operator et le `ServiceMonitor`. Le socle fournit :

- `deploy/observability/mq-prometheus-rules.yaml` ;
- `deploy/observability/grafana-dashboard-mq.yaml`.

Signaux principaux :

- profondeur de file ;
- âge du message le plus ancien ;
- DLQ et backout ;
- statut queue manager ;
- débit PUT/GET ;
- connexions/canaux ;
- stockage/logs ;
- état Native HA et changements de rôle.

### Important sur les noms de métriques

Les règles utilisent les noms du collecteur Prometheus IBM MQ couramment exposés (`ibmmq_queue_depth`, `ibmmq_queue_oldest_message_age`, `ibmmq_qmgr_status`). La documentation IBM précise que les noms disponibles peuvent varier selon version/collecteur. Avant d'activer les alertes en production, inventorier l'endpoint réel :

```bash
oc -n mayabank-mq-prod get pods
oc -n mayabank-mq-prod exec -it <pod-mq> -- sh -c 'curl -ks https://127.0.0.1:9157/metrics | grep "^ibmmq_" | head -100'
```

Adapter les règles uniquement après ce contrôle et conserver la liste des métriques dans `evidence/`.

## Runbook minimal

### Queue qui monte

1. identifier la queue, depth, oldest message et tendance PUT/GET ;
2. vérifier le nombre de consommateurs et les erreurs applicatives ;
3. vérifier canal/connexion, CPU, mémoire, stockage et latence DB ;
4. rechercher retry storm/backout/DLQ ;
5. ne pas purger une file sans validation métier ;
6. traiter la cause puis observer le drain.

### DLQ non vide

1. browse non destructif ;
2. lire MQDLH : reason, destination, queue manager d'origine ;
3. corréler avec logs applicatifs et changements récents ;
4. corriger la cause ;
5. rejouer uniquement avec un outil contrôlé, allow-list de destinations et audit ;
6. vérifier absence de doublon via idempotence métier.

### Connexion mTLS refusée

1. vérifier expiration et chaîne CA ;
2. vérifier DN/SAN attendu ;
3. contrôler CHLAUTH puis OAM ;
4. contrôler heure système ;
5. ne jamais désactiver CHLAUTH ou `SSLCAUTH(REQUIRED)` comme contournement de production.

### Stockage MQ proche de saturation

1. mesurer PVC, filesystem et croissance recovery logs ;
2. identifier queues accumulées et transactions longues ;
3. vérifier capacité/expansion de la StorageClass ;
4. augmenter selon procédure contrôlée ;
5. ne pas supprimer de fichiers sous `/var/mqm` manuellement.

## Critère de clôture I3

`RUNTIME_VALIDATED` seulement après :

- handshake mTLS positif + tests négatifs ;
- preuve des droits séparés producteur/consommateur ;
- endpoint métriques inventorié ;
- dashboard alimenté ;
- au moins une alerte testée ;
- runbook DLQ et panne MQ déroulé en lab.

Tant que ces preuves ne sont pas exécutées, le statut reste `DESIGNED / IMPLEMENTED / READY_TO_RUN`.

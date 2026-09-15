# I3 — Sécurité, observabilité et exploitation

## Objectif

Passer d'un POC fonctionnel à un socle présentable comme cible de production : mTLS, identités séparées, autorisations minimales, secrets hors Git, métriques MQ, alertes, dashboard et runbooks.

## Sécurité MQ

Le fichier `deploy/security/qm-prod-security.yaml` définit la cible :

- deux channels SVRCONN distincts et compatibles avec la limite IBM MQ de 20 caractères : `PAY.ORDER.SVRCONN` et `PAY.PROC.SVRCONN` ;
- `SSLCAUTH(REQUIRED)` et TLS 1.3 ;
- mapping certificat -> identité MQ avec `CHLAUTH` ;
- `paymentorder` : PUT sur REQUEST, GET/BROWSE sur RESPONSE ;
- `paymentproc` : GET/BROWSE sur REQUEST, PUT sur RESPONSE/BACKOUT ;
- aucun accès applicatif direct à la DLQ pour éviter le rejeu sauvage.

Les certificats et clés privées ne sont jamais versionnés. Le manifeste Native HA I4 référence des Secrets Kubernetes à créer depuis une PKI de lab ou, en cible, depuis Vault/External Secrets/cert-manager selon la plateforme.

### Validation runtime CRC — 15/09/2026

Validé sur OpenShift CRC mono-nœud avec IBM MQ Developer 9.4.5.1 :

- repository TLS généré et actif avec `CERTLABL(mayabank-server)` et `SSLKEYR(/run/runmqserver/tls/key)` ;
- `PAY.ORDER.SVRCONN` et `PAY.PROC.SVRCONN` en `SSLCAUTH(REQUIRED)` / `ANY_TLS13_OR_HIGHER` ;
- CHLAUTH certificat -> `paymentorder` / `paymentproc` ;
- OAM least privilege séparé producteur / processeur ;
- parcours mTLS positif `payment-order -> MQ -> payment-processing -> réponse` ;
- mauvais certificat/channel refusé avec `MQRC_NOT_AUTHORIZED (2035)` ;
- absence de certificat client refusée lors de la négociation du channel ;
- accès OAM interdit à `PAYMENT.BACKOUT.Q` depuis l'identité `paymentorder` avec `2035`.

Preuves : `evidence/i3-20260915/mtls-*.txt` et `evidence/i3-20260915/mq-security-runtime.txt`.

### Tests complémentaires non exécutés

- certificat signé par une CA non approuvée ;
- certificat expiré / révocation CRL ou OCSP.

Ces cas restent des extensions de la matrice PKI et ne doivent pas être présentés comme validés runtime.

## Compatibilité canal local I1/I2

Le montage d'une identité TLS dans l'image IBM Developer active aussi TLS sur `DEV.APP.SVRCONN`. Pour préserver les tests historiques locaux I1/I2 en authentification utilisateur/mot de passe, le ConfigMap applique explicitement :

```mqsc
ALTER CHANNEL('DEV.APP.SVRCONN') CHLTYPE(SVRCONN) SSLCIPH(' ') SSLCAUTH(OPTIONAL)
```

Le parcours password request/reply a été revalidé après cette correction. Cette exception est locale au POC ; les channels applicatifs cibles I3 restent en mTLS obligatoire.

## Observabilité

Le QueueManager I4 active l'endpoint Prometheus de l'Operator et le `ServiceMonitor`. Le socle fournit :

- `deploy/observability/mq-prometheus-rules.yaml` ;
- `deploy/observability/grafana-dashboard-mq.yaml`.

Signaux cibles de production :

- profondeur de file ;
- âge du message le plus ancien ;
- DLQ et backout ;
- statut queue manager ;
- débit PUT/GET ;
- connexions/canaux ;
- stockage/logs ;
- état Native HA et changements de rôle.

### Inventaire runtime CRC

L'endpoint métriques local a été inventorié et exposait 88 métriques `ibmmq_qmgr_*`. Le scrape User Workload Monitoring est validé avec `up == 1`.

Exemples réellement observés :

- `ibmmq_qmgr_queue_manager_file_system_free_space_percentage` ;
- `ibmmq_qmgr_log_file_system_free_space_percentage` ;
- `ibmmq_qmgr_log_write_latency_seconds` ;
- `ibmmq_qmgr_mqput_mqput1_total` ;
- `ibmmq_qmgr_destructive_get_total` ;
- `ibmmq_qmgr_failed_mqconn_mqconnx_total` ;
- métriques CPU/RAM du queue manager.

Preuves : `evidence/i3-20260915/mq-metric-names.txt`, `mq-metrics-raw.txt`, `prometheus-mq-up.txt` et `prometheus-ibmmq-count.txt`.

### Limite queue-level sur CRC

Le endpoint intégré testé ne fournit pas les séries `ibmmq_queue_depth`, `ibmmq_queue_oldest_message_age` ni `ibmmq_qmgr_status` attendues par le dashboard de référence. Le dashboard `deploy/observability/grafana-dashboard-mq.yaml` reste donc une **référence de cible**, non une preuve runtime CRC.

Pour obtenir la profondeur/âge par queue dans ce lab, ajouter un collecteur compatible tel que l'exporter des `mq-metric-samples`, puis adapter les requêtes après inventaire des noms réellement exposés.

### Alerte testée

Une règle locale de test sur l'espace filesystem du queue manager a été évaluée par Prometheus et observée en état firing. Preuves :

- `evidence/i3-20260915/prometheus-filesystem-free.txt` ;
- `evidence/i3-20260915/prometheus-alert-firing.txt` ;
- `evidence/i3-20260915/prometheus-rule-runtime.yaml`.

## Runbooks

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

Le parcours DLQ/rejeu contrôlé a été validé dans les itérations précédentes du POC.

### Panne MQ / reconnexion applicative

Le runbook a été exécuté sur CRC :

1. Argo CD self-heal suspendu temporairement ;
2. `deployment/mq` réduit de 1 à 0 ;
3. le client IBM MQ a détecté la rupture et lancé les tentatives de reconnexion ;
4. MQ restauré de 0 à 1 ;
5. `Completed reconnection` observé côté client ;
6. nouveau paiement request/reply réussi après reconnexion ;
7. Argo CD self-heal restauré.

Preuves : `evidence/i3-20260915/mq-outage-*.txt` et `password-channel-regression.txt`.

### Limite readiness identifiée

Pendant la reconnexion transparente gérée par le client IBM MQ, le pod `payment-processing` est resté `Ready`. Le marqueur local est supprimé uniquement lorsqu'une `JMSException` remonte au code applicatif ; la reconnexion automatique peut masquer temporairement cet état intermédiaire.

Conclusion :

- readiness initiale liée à une connexion MQ authentifiée : validée ;
- reconnexion automatique après panne MQ : validée ;
- readiness dynamique reflétant immédiatement toute panne MQ : **non validée**, amélioration à prévoir via health-check actif ou état explicite de connexion/reconnexion.

## Statut I3

### RUNTIME_VALIDATED sur CRC

- mTLS positif ;
- CHLAUTH et OAM least privilege ;
- tests négatifs principaux ;
- régression password I1/I2 ;
- endpoint métriques et scrape UWM ;
- inventaire des métriques ;
- une alerte Prometheus réellement firing ;
- panne MQ, reconnexion automatique et paiement post-reprise ;
- DLQ / backout / rejeu contrôlé issus des preuves précédentes du POC.

### RUNTIME PENDING / cible uniquement

- dashboard Grafana réellement alimenté avec les métriques queue-level ;
- exporter queue-level dédié ;
- CA non approuvée et expiration/révocation PKI ;
- readiness dynamique pendant reconnexion transparente ;
- Native HA multi-worker, qui relève de I4 et ne peut pas être revendiqué sur CRC mono-nœud.

I3 peut être présenté comme **sécurité + observabilité de base + résilience applicative runtime validées sur CRC**, avec les limites ci-dessus explicitement documentées. Ne pas présenter le dashboard queue-level ni Native HA comme validés tant qu'ils n'ont pas été exécutés sur un environnement adapté.

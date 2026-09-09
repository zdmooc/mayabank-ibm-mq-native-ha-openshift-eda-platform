# Lot paiement JMS — version 0.2.0

## But

Deux applications Java dans une même image versionnée :
- payment-order : Job de démonstration, publie un paiement fictif de 12,34 EUR puis attend une réponse corrélée.
- payment-processing : Deployment consommateur, valide le contrat, produit une réponse.

Ce lot n'ajoute pas encore d'API REST ni PostgreSQL. SIMULATED_PROCESSED signifie simulation validée, jamais débit bancaire ou écriture comptable.
Pas de garantie d'idempotence métier durable avant le lot base/inbox.
Le modèle request/reply transactionnel MQ évite d'introduire prématurément une transaction commune MQ/DB non démontrée.

## Construction

Java 17 ; client MQ 9.4.5.1 et dépendances Maven épinglés.
Build binaire OpenShift à partir des sources du clone, namespace mayabank-mq-build. Dockerfile utilise UBI OpenJDK 17 tag 1.23 (tag versionné mais mutable) ; digest de base à capturer dans les preuves de build.
Le déploiement applicatif utilise le digest produit par le build.
Le build Maven exécute les tests ; pas de skipTests.
Le namespace de build est séparé pour accéder aux registres/Maven sans élargir l'egress des applications.
Le rôle image-puller est accordé uniquement au service account payments dans le namespace de build.

## Authentification et réseau

Secret mq-app-credentials généré via OpenSSL, conservé dans Kubernetes.
L'image MQ Developer utilise MQ_DEV=true, MQ_CONNAUTH_USE_HTP=true et /run/secrets/mqAppPassword.
Les clients Java montent explicitement la clé mqAppPassword dans /etc/mayabank/mq ; MQ_PASSWORD_FILE indique le fichier à lire. Le montage du serveur MQ reste /run/secrets/mqAppPassword.
Le canal DEV.APP.SVRCONN exige CHCKCLNT(REQUIRED). Droits app limités aux queues de paiement ajoutées, mais les defaults Developer créent aussi DEV.* : ce n'est pas le modèle de sécurité cible production.
Les deux clients partagent app pour ce premier lot ; séparation producteur/consommateur et Vault prévues ensuite.
Pas de console admin ni Route. Service MQ ClusterIP uniquement.
NetworkPolicies autorisent les pods étiquetés mq-client du même namespace vers MQ:1414 et DNS ; les politiques sont additives par rapport au deny initial.
TCP sans TLS : authentification démontrée, confidentialité réseau non démontrée. Usage local/données fictives seulement ; ne pas exporter ce profil en cloud.
Le Job négatif doit recevoir précisément MQRC 2035, pas un simple timeout réseau.

## Transactions et limites

Producteur : message persistant, commit, attente de réponse par JMSCorrelationID.
Consommateur : consommation et émission de réponse persistante dans la même session transactionnelle MQ, puis commit.
Réponse avec expiration d'une heure ; requête sans expiration.
Contrat v1 : identifiant UUID, montant positif <= 1 000 000 EUR, deux décimales maximum.
Validation locale : six tests JUnit réussis avec compilation Java 17 ECJ ; image construite et poussée sur CRC selon les logs fournis. Intégration JMS encore en attente.
Un mécanisme de retries/backout applicatif est implémenté mais pas encore validé en intégration. Le provider JMS peut aussi utiliser BOQNAME/BOTHRESH : ne pas revendiquer une trajectoire poison avant test réel.
Le Deployment sans sonde applicative ne prouve pas une connexion MQ : seuls les Jobs et logs constituent la preuve du parcours.

## Exécution depuis le clone

~~~bash
git pull --ff-only
eval "$(crc oc-env)"
bash scripts/payments/deploy.sh
~~~

Le script demande DEPLOY-PAYMENTS et effectue :
1. Précontrôle CRC.
2. Build avec tests (peut durer plusieurs minutes).
3. Création du secret si absent (ne le remplace pas).
4. Mise à jour MQ et redémarrage contrôlé, conservation du même PVC.
5. Service et NetworkPolicies ciblées.
6. Déploiement du consommateur avec digest.
7. Test mauvais mot de passe, puis parcours paiement.

Arrêter en cas d'erreur et partager les logs sans secrets.
Ne pas relancer scripts/local-crc/deploy.sh après ce lot : il réappliquerait le profil initial MQ_DEV=false. La nouvelle source de configuration MQ est deploy/payments/mq-connected.yaml.
Une relecture smoke bindings via scripts/local-crc/verify.sh reste possible.

## Critères attendus

- Build Complete, six tests réussis dans les logs Maven.
- MQ Ready et consommateur démarré.
- PASS wrong password rejected (MQRC 2035).
- ACCEPTED puis PASS authenticated JMS request/reply avec le même UUID.
- Log SIMULATED_PROCESSED du consommateur avec cet UUID.
- Relever CPU/RAM et espace disque après build ; les images consomment le stockage partagé CRC.

## Diagnostic

Incident du 9 septembre 2026 : NoSuchFileException côté Java pour /run/secrets/mqAppPassword malgré une clé présente et un montage déclaré ; même Secret lisible côté MQ. La cause précise dans le conteneur Java reste non démontrée. Correctif : répertoire applicatif dédié /etc/mayabank/mq, projection explicite de la clé et chemin configurable. Reconstruire l'image et recréer les Jobs avec deploy.sh ; validation sur CRC requise. Aucune rotation du Secret nécessaire.

~~~bash
oc -n mayabank-mq-build get builds
oc -n mayabank-mq-local get pods,jobs
oc -n mayabank-mq-local logs deployment/mq --tail=100
oc -n mayabank-mq-local logs deployment/payment-processing --tail=100
oc -n mayabank-mq-local get events --sort-by=.lastTimestamp
~~~

Build Forbidden : lire le refus du build, pas d'attribution automatique de privilèges.
2035 avec bon secret : contrôler montage du secret, config MQ/CHLAUTH et simpleauth ; ne pas désactiver l'authentification.
Timeout : contrôler Service, endpoints, DNS et NetworkPolicies.
OOM : mesurer avant d'augmenter ; le build requiert temporairement 1Gi/limite2Gi, chaque client 256Mi/limite512Mi.
Une rotation du secret impose redémarrage MQ/clients et nouveau test ; elle n'est pas automatisée ici.

## Pause

Pour arrêter uniquement le consommateur :
~~~bash
oc -n mayabank-mq-local scale deployment payment-processing --replicas=0
~~~
Cela conserve les messages en attente dans MQ.
Les Jobs de vérification expirent après 24h : copier leurs sorties utiles avant expiration.
Pas de suppression automatique du namespace, du PVC, de Wero ou d'Argo CD.

## Références vérifiées

- [Client IBM Maven 9.4.5.1](https://repo.maven.apache.org/maven2/com/ibm/mq/com.ibm.mq.allclient/9.4.5.1/com.ibm.mq.allclient-9.4.5.1.pom)
- [Configuration Developer officielle versionnée](https://github.com/ibm-messaging/mq-container/blob/v9.4.5.1/docs/developer-config.md)
- [Lecture officielle des secrets et simpleauth](https://github.com/ibm-messaging/mq-container/blob/v9.4.5.1/internal/simpleauth/simpleauth.go)
- [Canaux et autorisations Developer](https://github.com/ibm-messaging/mq-container/blob/v9.4.5.1/incubating/mqadvanced-server-dev/10-dev.mqsc.tpl)
- [Images Red Hat OpenJDK](https://catalog.redhat.com/software/container-stacks/detail/613f8de2a5ebcd070d16407d)

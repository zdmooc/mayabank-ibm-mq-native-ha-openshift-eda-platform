# Lot local MQ — essai technique à exécuter

## Résultat des vérifications et décision

L'image **icr.io/ibm-messaging/mq:9.4.5.1-r1** est référencée par IBM.
Sa disponibilité depuis le réseau du poste, son démarrage sous les SCC de CRC et ses performances restent à tester.
La recherche n'a pas permis de confirmer la matrice de support opérateur IBM MQ / OpenShift 4.22.7. Ne pas affirmer cette compatibilité certifiée.

Décision : laboratoire mono-instance avec Deployment Recreate, image Developer officielle, sans opérateur pour ce premier essai.
L'opérateur reste une étape future avant Native HA ; aucune migration du PVC vers l'opérateur n'est présumée automatique.
Ce lot est une préparation technique, pas une validation IBM ni une plateforme prête pour production.

## État du poste communiqué le 9 septembre 2026

CRC 2.63.0, OpenShift/client oc 4.22.7, 8 vCPU, 24 Gio configurés.
Nœud Ready, aucun opérateur dégradé. Requests existantes : 5676m CPU / 18249Mi RAM.
Allocatable : 7800m / 24143212Ki. Marge théorique de requests : 2124m et environ 5,6 Gio.
Wero : environ 3,74 Gio utilisés au moment de la mesure ; ses workloads restent inchangés.
Argo CD présent : ne pas le réinstaller.
Stockage CRC : Retain, WaitForFirstConsumer, expansion désactivée.
PVC existants : registre 30Gi, Wero PostgreSQL 79Gi ; capacités déclarées, pas mesure d'occupation physique.
Le CRC indiquait environ 35 Go libres. Vérifier crc status juste avant déploiement.

## Périmètre précis

Namespace mayabank-mq-local, compte de service dédié, MQ QM.MAYABANK, PVC 5Gi.
Requests 500m CPU / 1Gi ; limites 1 CPU / 2Gi. Ce sont des valeurs de départ pour test léger, pas un sizing IBM validé.
Le total des requests mémoire atteindrait environ 82 %. Mesurer après démarrage.
Pas de Service ni Route : réseau entrant et sortant refusé pour ce namespace.
Pas de compte/password développeur par défaut, MQ_DEV=false ; console et métriques désactivées dans ce lot.
Les tests utilisent oc exec et les bindings locaux MQ. L'accès API Kubernetes d'un administrateur n'est pas bloqué par cette isolation.
Pas de modification Wero, Argo CD, SCC ou stockage existant.
Ne jamais ajouter anyuid/privileged pour masquer un échec : collecter l'erreur et revoir la conception.
Les applications, TLS, Vault et supervision viendront dans les lots suivants.

## Déploiement — Git Bash Windows

Cloner ce dépôt dans un nouveau dossier, ou git pull --ff-only dans le clone déjà existant.
Depuis la racine :

~~~bash
eval "$(crc oc-env)"
crc status
bash scripts/local-crc/preflight.sh
bash scripts/local-crc/deploy.sh
bash scripts/local-crc/verify.sh
~~~

deploy.sh affiche la licence à accepter via confirmation explicite ; le lien est ci-dessous.
Le manifeste contient LICENSE=accept : ne pas l'appliquer directement sans avoir accepté les conditions.
Les scripts sont lancés avec bash, pas besoin de chmod sous Windows.
Le smoke test consomme uniquement LAB.SMOKE.Q. Ne pas y stocker de messages métier.
Le tag est épinglé ; enregistrer imageID/digest réel après pull pour figer la preuve.

## Résultat attendu

PVC Bound après création du pod ; MQ Ready ; QM.MAYABANK actif.
Queues PAYMENT.* créées. Message de test retrouvé, puis verdict PASS.
Ces résultats restent ATTENDUS tant que les sorties de l'utilisateur ne sont pas enregistrées.

## Test de persistance optionnel, après smoke test

Dans le même terminal Git Bash :

~~~bash
export MSYS_NO_PATHCONV=1
printf 'MAYABANK-RECOVERY-001\n\n' | oc -n mayabank-mq-local exec -i deployment/mq -- /opt/mqm/samp/bin/amqsput LAB.RECOVERY.Q QM.MAYABANK
oc -n mayabank-mq-local rollout restart deployment/mq
oc -n mayabank-mq-local rollout status deployment/mq --timeout=600s
oc -n mayabank-mq-local exec deployment/mq -- /opt/mqm/samp/bin/amqsget LAB.RECOVERY.Q QM.MAYABANK
~~~

Vérifier manuellement que le marqueur est retrouvé, enregistrer les heures et le nouveau pod.
Il s'agit d'un redémarrage contrôlé avec volume, pas d'une panne brutale ni d'une bascule HA.
DLQ/backout configurées ne signifie pas que le routage poison a été testé.

## Diagnostic

~~~bash
oc -n mayabank-mq-local get pods,pvc
oc -n mayabank-mq-local get events --sort-by=.lastTimestamp
oc -n mayabank-mq-local describe deployment mq
oc -n mayabank-mq-local logs deployment/mq --tail=100
~~~

- ImagePullBackOff : vérifier registre/tag/réseau ; ne pas remplacer par latest.
- Pending : lire événements PVC/ressources ; WaitForFirstConsumer attend le consommateur.
- Forbidden/SCC : conserver le refus exact, pas de changement de sécurité automatique.
- Permission denied sur /mnt/mqm : examiner droits/provisioner ; pas de chown global.
- OOMKilled : collecter consommation et limites avant de redimensionner.
- Un changement de ConfigMap monté avec subPath demande un redémarrage ; valider comment MQSC est réappliqué avant modifications.

## Pause et nettoyage

Pour arrêter seulement ce MQ sans supprimer son volume :
~~~bash
oc -n mayabank-mq-local scale deployment mq --replicas=0
~~~
Pour reprendre, replicas=1, puis rollout status.
Ne pas utiliser delete -f : cela inclurait namespace/PVC. La suppression définitive attend sauvegarde et inventaire des PV Retain.
Ne pas réduire un PVC en place.

## Sources consultées

- [Image développeur IBM](https://www.ibm.com/docs/en/ibm-mq/9.4.x?topic=reference-mq-advanced-developers-container-image)
- [Usage versionné 9.4.5.1](https://github.com/ibm-messaging/mq-container/blob/v9.4.5.1/docs/usage.md)
- [Configuration Developer](https://github.com/ibm-messaging/mq-container/blob/v9.4.5.1/docs/developer-config.md)
- [Licence et restrictions Developer](https://github.com/ibm-messaging/mq-container/blob/v9.4.5.1/README.md#license)
- [Support opérateur à confirmer](https://www.ibm.com/docs/en/ibm-mq/9.4.x?topic=containers-version-support-mq-operator)

La licence Developer ne doit pas être transposée automatiquement au cluster GCP : confirmer les droits d'évaluation/déploiement distant avant la campagne cloud.

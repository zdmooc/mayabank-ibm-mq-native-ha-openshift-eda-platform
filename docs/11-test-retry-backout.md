# Test message invalide, retry et backout

Statut : code livré, compilation Java 17 et syntaxe Bash contrôlées. Aucune exécution de ce nouveau lot sur CRC n'est encore attestée.

## Exécution depuis Git Bash

```bash
git pull --ff-only &&
eval "$(crc oc-env)" &&
bash scripts/payments/deploy.sh &&
bash scripts/payments/verify-backout.sh
```

Le déploiement demande DEPLOY-PAYMENTS. Il reconstruit Java, recharge MQ et redéploie le processeur avec le digest construit. Le test JMS standard est exécuté avant le nouveau test. Cette étape peut interrompre brièvement MQ. Wero et les PVC ne sont pas supprimés.

## Vérifications

1. Le Job backout-test vérifie son accès en consultation à BACKOUT avant de publier.
2. Publication d'un TextMessage persistant au contrat invalide, avec une corrélation UUID unique.
3. Le processeur effectue un rollback aux livraisons 1 et 2 et journalise RETRY avec cet identifiant.
4. À la troisième livraison, le processeur publie le message rejeté en BACKOUT et acquitte la demande dans la même transaction JMS.
5. La copie porte backoutOrigin=application, originalDeliveryCount=3 et failureReason.
6. Le Job consulte BACKOUT par QueueBrowser et sélecteur : il exige le contenu exact et ces propriétés. Il ne consomme pas le message.
7. Il vérifie qu'aucune demande ni réponse avec cette corrélation n'est visible dans REQUEST/RESPONSE.
8. Le script exige les deux traces RETRY et la trace BACKOUT du processeur depuis le début du test.
9. Un nouveau Job de paiement valide doit obtenir sa réponse corrélée.

Succès final attendu :

```text
PASS lot backout : message invalide isolé puis paiement valide.
```

## Périmètre et limites

- Le compte app gagne uniquement BROWSE sur BACKOUT en plus de PUT/INQ déjà présents, pour ce test local. Aucun GET ajouté. Séparation des identités de production à traiter dans le lot sécurité.
- Aucun CLEAR, aucune purge, aucune suppression de message de backout. Chaque succès laisse un message de preuve ; surveiller la profondeur et prévoir ultérieurement un archivage/nettoyage explicite.
- La politique applicative utilise JMSXDeliveryCount avec un seuil de 3 ; BOTHRESH reste 3 sur REQUEST. Le test exige une copie identifiée comme applicative et les traces correspondantes. Il ne doit pas déclarer réussi un déplacement effectué uniquement par un autre mécanisme.
- Le contrôle d'absence sur les files est une observation à cet instant, pas une garantie générale d'exactly-once.
- Aucun test DLQ, rejeu ou idempotence durable dans ce lot.
- Un consommateur externe des files ou une autre réplique pourrait perturber les observations : exécuter sur le POC local mono-processeur.
- Un échec peut laisser le message sur REQUEST ou BACKOUT. Garder l'identifiant affiché et les logs ; ne pas purger pour masquer le problème.
- Après exécution, enregistrer dans evidence/ le commit, le digest, l'identifiant, les traces RETRY/BACKOUT et le verdict du paiement valide.

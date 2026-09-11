# DLQ applicative : destination inexistante et MQDLH

Statut : code compilé en Java 17 ; sérialisation/désérialisation MQDLH et rejets de contenu/code erronés testés localement. Test CRC non exécuté à ce stade.

## Exécuter

Depuis le dépôt MQ dans Git Bash, CRC démarré et connexion kubeadmin active :

```bash
git pull --ff-only &&
eval "$(crc oc-env)" &&
bash scripts/payments/deploy.sh &&
bash scripts/payments/verify-dlq.sh
```

Saisir DEPLOY-PAYMENTS. Le build exécute aussi les tests Maven, reconstruit l'image, recharge MQ et redéploie le processeur. Le script de déploiement valide d'abord l'authentification et le paiement habituel.

## Scénario et preuve attendue

Le Job utilise le client Java IBM MQ de bas niveau, avec MQCSP et le Secret existant. Il ouvre PAYMENT.DLQ en écriture et consultation avant le scénario. Le compte app reçoit PUT/BROWSE/INQ uniquement sur cette file, sans GET.

1. Générer un identifiant et une destination LAB.MISSING.<UUID> qui n'est pas créée.
2. Tenter MQOPEN en sortie ; exiger exactement MQRC_UNKNOWN_OBJECT_NAME (2085). Tout autre code fait échouer le test. Si la file existe, arrêter sans publier.
3. L'application crée explicitement un message persistant avec MQMD.Format=MQDEAD et un MQDLH : motif 2085, destination originale, gestionnaire, format du corps, encodage et application d'origine.
4. Publier en DLQ sous transaction et effectuer commit.
5. Relire en mode BROWSE par CorrelId binaire unique ; vérifier MQMD, MQDLH et corps exact sans retirer le message.
6. Exécuter un paiement JMS valide après cette vérification.

Verdict final attendu :

```text
PASS lot DLQ applicative : MQDLH 2085 vérifié puis paiement valide.
```

## Limites précises

- La destination n'est pas ouverte, donc aucun MQPUT n'est tenté vers cette destination. La non-livraison provient de MQOPEN.
- Le placement en DLQ est explicite dans cette application de test. Ce lot ne démontre pas un routage automatique par un canal ou un gestionnaire MQ.
- Il ne modifie pas le chemin métier PaymentOrder. Il fournit un scénario dédié d'erreur de destination et un message MQDLH exploitable pour un futur rejeu.
- Ce n'est pas une preuve d'absence de perte entre l'erreur et la publication en DLQ : le corps est en mémoire jusqu'au commit.
- Chaque essai laisse un message de preuve en DLQ. Aucun CLEAR, aucun GET destructif, aucun remplacement des messages existants.
- Un échec après publication peut laisser son message en DLQ : conserver probeId et logs.
- Le test backout précédent porte sur les échecs de traitement avec rollback ; ce lot porte sur une destination introuvable.
- DLQ automatique via canal, rejeu contrôlé, gestionnaire de DLQ et idempotence durable restent à construire et tester.
- Conserver les preuves CRC avec le commit, le digest, probeId, reason et le PASS du paiement suivant. Ne pas marquer le test exécuté avant réception des sorties.

Les tests unitaires DlqTestTest couvrent la structure MQDLH, le corps UTF-8 et le refus d'un corps ou motif erroné. Ils ne nécessitent pas de cluster.

# Preuve : paiement JMS authentifié sur CRC

Rapport enregistré le 10 septembre 2026 à partir de la sortie du terminal fournie par l'utilisateur. Commandes exécutées sur son CRC ; aucune exécution distante du cluster par l'assistant. Commit local testé : c0d6dfa.

## Exécution

```bash
git pull --ff-only &&
oc -n mayabank-mq-local exec deployment/mq -- setmqaut -m QM.MAYABANK -t queue -n PAYMENT.REQUEST.Q -p app +browse &&
bash scripts/payments/verify.sh
```

## Résultat observé

```text
The setmqaut command completed successfully.
PASS: wrong password rejected (MQRC 2035)
ACCEPTED paymentId=cfdfcc06-67ef-4e79-8a75-fe1d8fd2b1d4
PASS: authenticated JMS request/reply paymentId=cfdfcc06-67ef-4e79-8a75-fe1d8fd2b1d4
READY: authenticated MQ connection; simulation only
SIMULATED_PROCESSED paymentId=b70eacbf-6f51-45e9-84ad-b5865ab4d619
SIMULATED_PROCESSED paymentId=cfdfcc06-67ef-4e79-8a75-fe1d8fd2b1d4
PASS lot paiement : refus mauvais mot de passe puis demande/réponse JMS.
```

Le tail du processeur contient également des lignes JMSWMQ2008 précédant READY. Elles correspondent aux tentatives avant la reprise visible ; cette sortie ne démontre pas une absence d'erreurs sur une période prolongée.

## Ce que cette preuve valide

- Refus d'un mauvais mot de passe, complété par une connexion positive réussie.
- Publication JMS authentifiée, consommation et traitement simulé, retour d'une réponse corrélée au paiement.
- Reprise du processeur après ajout du droit BROWSE sur PAYMENT.REQUEST.Q.
- Traitement de la demande antérieure b70eacbf-6f51-45e9-84ad-b5865ab4d619 restée en attente. Le client de cette ancienne demande avait déjà expiré : son aller-retour n'est pas validé rétroactivement.

## Limites et suite

- Simulation locale, TCP interne sans TLS ; aucun paiement bancaire réel.
- CRC à un nœud : aucune preuve Native HA ou de tolérance à la perte d'un nœud.
- Ni charge, ni idempotence durable, ni retry/backout/DLQ validés par ce test.
- Le correctif d'ordre HTP/OAM dans autocfg/base_qm.ini a été appliqué manuellement auparavant. Son automatisation a ensuite été ajoutée dans scripts/payments/ensure-htp-order.sh. La validation complète sur un volume neuf reste à effectuer.
- BROWSE figure dans le manifeste Git pour les files REQUEST et RESPONSE. La commande ci-dessus l'applique immédiatement à REQUEST dans le gestionnaire actif ; un git pull seul n'applique pas le ConfigMap au cluster.
- DEBUG retiré et test après redémarrage réussi : voir ci-dessous.

## Deuxième exécution : DEBUG retiré et redémarrage contrôlé

Sortie fournie par l'utilisateur après `oc set env deployment/mq DEBUG-`, attente du rollout et lancement de verify.sh :

```text
PASS: wrong password rejected (MQRC 2035)
ACCEPTED paymentId=50e6a15c-0a48-40b1-8aa4-e327c627e89a
PASS: authenticated JMS request/reply paymentId=50e6a15c-0a48-40b1-8aa4-e327c627e89a
READY: authenticated MQ connection; simulation only
SIMULATED_PROCESSED paymentId=50e6a15c-0a48-40b1-8aa4-e327c627e89a
PASS lot paiement : refus mauvais mot de passe puis demande/réponse JMS.
```

Le processeur a journalisé JMSWMQ2002 puis JMSWMQ0018 pendant l'interruption avant le nouveau READY. La reconnexion et un nouvel aller-retour sont validés après redémarrage ; aucun RTO n'a été mesuré. Ce test réutilise le PVC existant et ne prouve pas une installation neuve.

## Troisième exécution : contrôle automatisé HTP sur CRC

Commit testé : a27fa6a. Sortie fournie par l'utilisateur après ensure-htp-order.sh puis verify.sh :

```text
Base HTP/OAM déjà correcte.
PASS : HTP précède OAM dans qm.ini.
PASS: wrong password rejected (MQRC 2035)
ACCEPTED paymentId=ff2f23cd-0acf-402e-88de-40cb6934e90b
PASS: authenticated JMS request/reply paymentId=ff2f23cd-0acf-402e-88de-40cb6934e90b
SIMULATED_PROCESSED paymentId=ff2f23cd-0acf-402e-88de-40cb6934e90b
PASS lot paiement : refus mauvais mot de passe puis demande/réponse JMS.
```

Le chemin « déjà correct » du script est validé sur le cluster : aucun redémarrage demandé et nouveau paiement réussi. Le tail contient toujours les anciens messages d'erreur et paiements des exécutions précédentes ; ils ne constituent pas de nouveaux échecs de ce test. La branche de réparation automatique sur une base OAM seule a été testée sur fichiers simulés, mais pas encore sur un gestionnaire neuf. Cette preuve ne valide pas encore la reproductibilité complète depuis un volume neuf.

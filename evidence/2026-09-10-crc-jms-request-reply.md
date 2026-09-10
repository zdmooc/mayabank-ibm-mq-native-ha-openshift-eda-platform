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
- Le correctif d'ordre HTP/OAM dans autocfg/base_qm.ini a été appliqué manuellement auparavant. Son automatisation et la reproductibilité sur un volume neuf restent à finaliser.
- BROWSE figure dans le manifeste Git pour les files REQUEST et RESPONSE. La commande ci-dessus l'applique immédiatement à REQUEST dans le gestionnaire actif ; un git pull seul n'applique pas le ConfigMap au cluster.
- Retirer DEBUG du Deployment MQ après diagnostic, attendre sa disponibilité et rejouer ce test après redémarrage contrôlé.

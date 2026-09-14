# I1 — Fiabilité métier, idempotence et reprise

## Objectif

Fermer le socle métier avant l'industrialisation : doublon, conflit, redémarrage du processeur, interruption après commit PostgreSQL mais avant commit MQ, retry/backout, DLQ applicative et retour à un paiement nominal.

## État du code

Le dépôt contient déjà :

- un ledger PostgreSQL avec contrainte métier sur `payment_id` ;
- un effet métier et la trace d'idempotence commités dans la même transaction PostgreSQL ;
- les réponses `NEW`, `DUPLICATE` et `CONFLICT` ;
- une sonde contrôlée `MAYABANK_ENABLE_CRASH_PROBE` qui arrête le processeur après le commit DB et avant le commit MQ ;
- une vérification de redelivery MQ et de l'unicité de l'effet métier ;
- retry/backout, DLQ applicative et rejeu transactionnel déjà couverts par les lots précédents ;
- une readiness OpenShift maintenant liée à la présence d'une connexion MQ authentifiée active. La liveness n'est volontairement pas couplée à MQ : le processus sait reconnecter et ne doit pas être tué à chaque indisponibilité du broker.

## Validation CRC à exécuter

> Statut : **READY_TO_RUN**. Ne pas marquer cette itération `RUNTIME_VALIDATED` avant obtention des sorties ci-dessous sur CRC.

```bash
cd "$HOME/workspaces/mayabank-ibm-mq-native-ha-openshift-eda-platform"
git fetch origin
git switch feature/covea-eda-i1-i4
git pull --ff-only

eval "$(crc oc-env)"
crc status
oc whoami
oc get nodes

bash scripts/payments/deploy.sh
bash scripts/idempotency/verify.sh
```

La commande `verify.sh` doit prouver successivement :

1. premier paiement -> `NEW` ;
2. même identifiant et même payload -> `DUPLICATE` ;
3. même identifiant avec contenu différent -> `CONFLICT` ;
4. redémarrage du processeur puis même paiement -> toujours `DUPLICATE` ;
5. crash injecté après commit DB -> redelivery MQ avec `JMSXDeliveryCount >= 2` ;
6. après redelivery, un seul effet métier existe ;
7. la sonde de crash est retirée ;
8. le parcours JMS nominal repasse au vert.

## Contrôle readiness

```bash
oc -n mayabank-mq-local get pods -l app=payment-processing -w
```

Pendant une connexion MQ valide, le pod doit être `Ready`. Si MQ est indisponible, le processus reste vivant mais la readiness doit passer à `False` jusqu'à reconnexion.

## Ce que l'on peut dire en entretien après validation

- La livraison MQ reste `at-least-once` ; l'application rend l'effet métier idempotent par une clé métier durable.
- Une panne après commit DB mais avant ACK/commit MQ provoque une redelivery, pas un second effet métier.
- `exactly-once` n'est pas revendiqué comme propriété magique du transport ; la cohérence est obtenue par transaction locale DB + déduplication durable + redelivery contrôlée.
- La readiness expose la disponibilité réelle du consommateur vis-à-vis de MQ, tandis que la liveness mesure la santé du processus.

## Critère de clôture I1

I1 est `RUNTIME_VALIDATED` uniquement lorsque les sorties CRC sont conservées dans `evidence/` avec date, commit Git et identifiants des scénarios. Sinon son statut reste `IMPLEMENTED / READY_TO_RUN`.

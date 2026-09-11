# Rejeu contrôlé sur files dédiées

Statut : code compilé Java 17 ; extraction MQDLH, liste de destinations autorisées, transfert UTF-8 et rejet de contenu altéré vérifiés localement ; syntaxe Bash contrôlée. Exécution CRC réussie le 11 septembre 2026 : [preuve](../evidence/2026-09-11-crc-rejeu-controle.md).

## Commandes

Depuis le clone MQ, CRC disponible et connexion kubeadmin active :

```bash
git pull --ff-only &&
eval "$(crc oc-env)" &&
bash scripts/payments/deploy.sh &&
bash scripts/payments/verify-replay.sh
```

Saisir DEPLOY-PAYMENTS. Une reconstruction Java et un redémarrage MQ sont nécessaires. Les tests unitaires Maven sont exécutés par le build. Le script valide le paiement standard avant le scénario de rejeu.

## Isolation

Quatre files persistantes LAB.REPLAY.SOURCE.Q, TARGET.Q, BLOCKED.Q et AUDIT.Q sont définies. BLOCKED est interdite en écriture. Les droits sont limités à ces noms ; GET n'est ajouté que sur SOURCE.

Le Job génère une sonde REPLAY-PROBE et un MQDLH de test avec motif 2085 et destination TARGET. Cette enveloppe est une donnée de test préparée, pas le résultat d'une nouvelle erreur 2085. Il ne lit ni ne consomme les messages de PAYMENT.DLQ ou PAYMENT.BACKOUT.Q.

## Contrôles attendus

1. Publication de la sonde source et commit.
2. Inspection par BROWSE, sans retrait ; validation du MQDLH, de l'identité et du corps, destination strictement TARGET.
3. GET source sous syncpoint puis tentative vers BLOCKED, destination fixe utilisée uniquement pour injecter la panne.
4. Exiger MQRC_PUT_INHIBITED 2051 ; backout de la transaction.
5. Vérifier que le message source original (même MsgId, CorrelId et corps) est conservé, sans message correspondant sur BLOCKED, TARGET ou AUDIT.
6. GET du même message source sous syncpoint, extraction du corps, PUT persistant vers TARGET et PUT de l'audit dans la même transaction MQ, puis commit.
7. Vérifier source absente, contenu cible exact et audit persistant consultés sans retrait.
8. Un second GET de la source sélectionnée doit retourner 2033, sans nouvelle publication.
9. Un paiement JMS valide doit réussir ensuite.

Verdict final attendu :

```text
PASS lot rejeu : rollback sans perte, transfert et audit vérifiés puis paiement valide.
```

## Portée et limites

- Il s'agit d'un test de rejeu isolé, pas d'un outil générique d'administration des DLQ.
- L'inspection est une phase du test ; aucun mode CLI autonome dry-run n'est livré.
- La destination normale est codée explicitement et revalidée après le GET. La destination BLOCKED n'est utilisée que dans l'injection de panne.
- Les opérations source/cible/audit utilisent un seul gestionnaire et une seule unité de travail MQ. Aucun test de panne pendant commit n'est effectué.
- Le test de rollback ne prouve pas une idempotence métier durable. Le refus d'un second GET de la source ne protège pas contre une autre publication du même paiement.
- La cible contient le corps de la sonde, sans MQDLH, avec un nouveau MsgId et le CorrelId conservé. L'audit contient probeId, source, cible et CorrelId.
- Chaque succès conserve une sonde cible et un audit ; aucune purge. La seule consommation attendue est celle du message source créé par le test.
- Pas de garantie de sécurité d'exploitation complète avec le compte app partagé : séparation des identités prévue dans le lot sécurité.
- Le test de DLQ applicative existant reste disponible via verify-dlq.sh ; le client de connexion a été extrait en méthode commune sans changer ses paramètres.
- Après exécution, enregistrer digest, probeId, CorrelId, verdict rollback, verdict commit et paiement suivant dans evidence/.

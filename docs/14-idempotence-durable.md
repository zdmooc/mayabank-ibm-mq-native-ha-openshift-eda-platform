# Idempotence durable — lot à valider sur CRC

Statut : code et scripts livrés ; aucune exécution PostgreSQL/JMS de ce lot encore attestée sur CRC.

## Comportement et transactions

Le processeur utilise désormais une base PostgreSQL dédiée `payments-db` dans `mayabank-mq-local`. Aucun accès à la base Wero.

1. Recevoir le paiement dans une session MQ transactionnelle.
2. Insérer sa clé métier `paymentId`, son contrat canonique et sa réponse dans `payment_inbox`. La clé primaire arbitre les doublons avec `ON CONFLICT DO NOTHING`.
3. Pour une nouvelle clé uniquement, écrire un effet métier simulé dans `payment_effect`, dans la même transaction PostgreSQL, puis committer la base.
4. Publier la réponse et acquitter la demande ensemble par commit MQ.

Un doublon identique réutilise la réponse enregistrée. Le même ID avec un montant différent reçoit `CONFLICT|<id>` et ne modifie pas le premier effet. Les contrats invalides continuent de suivre le retry/backout existant.

La lecture du doublon utilise une seconde requête en isolation READ COMMITTED, après l'insertion concurrente éventuelle. Les requêtes sont paramétrées. En cas d'erreur DB, la réception MQ est annulée et réessayée après cinq secondes.

**Ce n'est pas une transaction distribuée XA.** Après un commit PostgreSQL et un arrêt avant commit MQ, la demande peut être relivrée ; le registre évite un second effet en base. Une nouvelle demande identique peut obtenir une nouvelle réponse. La garantie porte sur l'effet simulé dans cette base, pas sur un virement externe ni une livraison réseau « exactly once ».

Les paiements des lots précédents ne sont pas rétroactivement enregistrés. Ne pas purger le registre pendant la période où des demandes peuvent être rejouées. Rétention, sauvegarde/restauration coordonnée et indisponibilité prolongée de la base restent à concevoir.

## Ressources et image

- PostgreSQL : requêtes 100m CPU / 256Mi RAM ; limites 500m / 512Mi ; PVC demandé 2Gi.
- Aucun port externe ; TCP 5432 autorisé uniquement depuis les pods clients MQ du namespace.
- Compte DB du laboratoire dans un Secret Kubernetes, sans valeur dans Git ni affichage par les scripts. TLS et séparation fine des rôles SQL restent à traiter.
- PostgreSQL 16 : digest résolu depuis l'ImageStream OpenShift `postgresql:16`, ou import de l'image communautaire `quay.io/sclorg/postgresql-16-c9s:latest` si absent. Les déploiements suivants conservent le digest installé. Le tag de bootstrap n'est pas un verrou global reproductible : conserver le digest affiché dans la preuve CRC.
- Pilote JDBC épinglé à 42.7.7. Pas de nouvelle dépendance sur un service cloud.
- Le stockage CRC hostpath partage le disque de la VM : 2Gi demandés ne constituent pas un quota physique garanti.

Le dernier contrôle utilisateur indiquait environ 22 Go libres, des réservations CPU à 81 % et mémoire à 83 %. Recontrôler si d'autres workloads ont été ajoutés.

## Exécution depuis Git Bash

```bash
cd "$HOME/workspaces/mayabank-ibm-mq-native-ha-openshift-eda-platform" &&
git pull --ff-only &&
eval "$(crc oc-env)" &&
bash scripts/payments/deploy.sh &&
bash scripts/idempotency/verify.sh
```

Confirmer `DEPLOY-PAYMENTS` au prompt existant. Le déploiement construit Java, prépare PostgreSQL et le schéma, puis redéploie MQ et le processeur. Attendre le contrôle JMS initial avant les sondes d'idempotence.

Les options strictes sont exécutées par les scripts `bash`, pas dans le terminal interactif. Le déploiement DB se fait via le script, qui remplace l'image par son digest ; ne pas appliquer directement `database.yaml`.

## Preuves exigées

| Sonde | Vérification |
|---|---|
| baseline | NEW, puis DUPLICATE, puis CONFLICT pour un montant différent ; exactement un effet inchangé en base |
| after-restart | même paiement après remplacement du pod processeur ; DUPLICATE et effet unique |
| crash | nouvelle clé ; arrêt JVM code 75 après commit DB ; redémarrage du conteneur ; réponse DUPLICATE avec compteur de livraison MQ >= 2 ; effet unique |
| contrôle final | injection désactivée ; mauvais mot de passe rejeté et paiement JMS valide |

L'injection exige simultanément une variable temporaire sur le processeur, une propriété sur la sonde et un résultat NEW. Elle utilise `Runtime.halt(75)` pour interrompre réellement la JVM avant réponse/acquittement MQ. Le script vérifie la trace du conteneur précédent et son code de sortie. Le doublon ne déclenche pas un nouvel arrêt. La sortie du script retire la variable et attend le redéploiement ; si ce nettoyage échoue, le script le signale et retourne un échec.

Les IDs sont uniques à chaque exécution. Aucun vidage des queues ou des tables ; les Jobs conservent leurs logs temporairement. Les réponses des sondes sont consommées par leurs tests.

Conserver : commit Git, digest Java et PostgreSQL, logs des trois Jobs, trace `CRASH_AFTER_DB_COMMIT`, résultat final, consommation de ressources et taille du stockage. Ne pas déclarer le lot validé avant réception de ces résultats.

## Vérifications effectuées avant publication

- Compilation Java 17 du registre, du processeur et du test avec les interfaces JMS locales : réussie.
- Syntaxe Bash des scripts et lecture des manifests YAML : vérifiées.
- Pas de PostgreSQL ni de cluster CRC accessibles dans l'environnement de préparation : transactions SQL, compatibilité de l'image/SCC, build Maven complet et scénarios de crash restent à valider avec les commandes ci-dessus.
- La reprise sur redémarrage PostgreSQL, les accès concurrents sous charge et la restauration d'une sauvegarde ne sont pas prouvés par ce lot.

## Références

- [PostgreSQL 16 : INSERT / ON CONFLICT](https://www.postgresql.org/docs/16/sql-insert.html).
- [Image PostgreSQL SCLorg : variables, volume et usage OpenShift](https://github.com/sclorg/postgresql-container/blob/master/16/README.md).
- [Version du pilote pgJDBC](https://github.com/pgjdbc/pgjdbc/blob/REL42.7.7/gradle.properties).

# Correctif HTP local et reproductibilité

## Cause observée

Après passage du gestionnaire local au mode Developer avec clients authentifiés, autocfg/base_qm.ini conservait uniquement le composant OAM. Une modification du seul qm.ini était écrasée au redémarrage. L'ajout de HTP avant OAM dans la base a permis la connexion positive. Les autorisations BROWSE des files REQUEST et RESPONSE ont ensuite permis le traitement JMS.

## Automatisation

```bash
bash scripts/payments/ensure-htp-order.sh &&
bash scripts/payments/verify.sh
```

Ce correctif est limité au cluster mono-nœud nommé crc, au namespace mayabank-mq-local, au gestionnaire QM.MAYABANK et au digest MQ Developer actuellement épinglé. Il accepte une base contenant seulement OAM, ou HTP puis OAM. Toute autre forme de ServiceComponent est refusée avant écriture. Si la base manque, il s'arrête.

Si nécessaire, le script sauvegarde la base sur le même PVC, prépare et contrôle un fichier temporaire puis le remplace. Il redémarre MQ uniquement après correction ou si l'ordre actif doit être rechargé. Une relance sur une configuration correcte ne modifie pas la base. Aucun Secret n'est affiché. Les options set -eu sont confinées aux processus du script, pas au terminal interactif.

Le script deploy.sh appelle ce contrôle après chargement du manifeste et redémarrage explicite de MQ pour relire le ConfigMap monté via subPath. Ce déploiement complet reconstruit aussi l'application ; il n'est pas nécessaire pour vérifier uniquement le correctif sur une installation existante.

## Validation

Tests locaux sur fichiers simulés : base OAM corrigée ; base déjà correcte inchangée ; ordre actif incorrect déclenchant un redémarrage demandé ; module inconnu refusé ; HTP dupliqué refusé. Syntaxe Bash contrôlée. Ces tests ne remplacent pas l'exécution sur MQ.

Le correctif manuel et le paiement après redémarrage ont été validés sur le PVC existant (voir evidence/2026-09-10-crc-jms-request-reply.md). Le nouveau script a été exécuté avec succès sur CRC au commit a27fa6a : base déjà correcte, aucun redémarrage demandé et nouveau paiement JMS validé (ff2f23cd-0acf-402e-88de-40cb6934e90b). La branche de réparation sur une base OAM seule reste à valider sur MQ ; ses tests actuels utilisent des fichiers simulés. Une installation complète avec stockage neuf n'est pas encore validée : conserver le PVC actuel et prévoir un environnement isolé pour ce test. Aucune suppression de PVC n'est requise pour l'étape courante.

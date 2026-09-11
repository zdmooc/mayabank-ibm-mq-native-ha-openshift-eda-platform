# Preuve CRC : rejeu contrôlé — 11 septembre 2026

Source : sortie du terminal fournie par l'utilisateur après deploy.sh puis verify-replay.sh. Aucun accès direct de l'assistant au cluster. Commit exact du clone absent de cet extrait ; lot livré jusqu'au commit 9bb6b933.

Image publiée :
`image-registry.openshift-image-registry.svc:5000/mayabank-mq-build/payments@sha256:95fed63b98f7e54cda973acca051ac071e380e6c07b5b6dd352dea6c80ac1894`

Déploiements MQ et processeur réussis. Base HTP/OAM correcte. Mauvais mot de passe rejeté. Paiement initial validé : 63d3b9e7-5b9a-446c-9cf9-bbb301c6485f.

```text
REPLAY_SEEDED probeId=f4ad72b1-7dc8-4861-a0f7-a26654649e2a correlId=6ff7dde83bb379ee66b2234ca234ea42de4a862e2146a4e9
INSPECT_ONLY source=LAB.REPLAY.SOURCE.Q destination=LAB.REPLAY.TARGET.Q probeId=f4ad72b1-7dc8-4861-a0f7-a26654649e2a
PASS: replay failure 2051 rolled back; original source preserved; probeId=f4ad72b1-7dc8-4861-a0f7-a26654649e2a
PASS: replay committed; source absent; target and audit verified; second source GET=2033; probeId=f4ad72b1-7dc8-4861-a0f7-a26654649e2a
ACCEPTED paymentId=821845f3-f72f-411f-a72e-e2c53be9ab7f
PASS: authenticated JMS request/reply paymentId=821845f3-f72f-411f-a72e-e2c53be9ab7f
SIMULATED_PROCESSED paymentId=821845f3-f72f-411f-a72e-e2c53be9ab7f
PASS lot rejeu : rollback sans perte, transfert et audit vérifiés puis paiement valide.
```

## Ce qui est validé

- Inspection non destructive d'une sonde préparée avec MQDLH dans LAB.REPLAY.SOURCE.Q.
- Échec 2051 sur la destination de panne dédiée, rollback et conservation du message source original.
- Transfert du même message vers la cible autorisée et audit persistant dans une transaction MQ.
- Après commit : source absente, contenu cible et audit vérifiés, second GET source retournant 2033.
- Nouveau paiement JMS réussi après le test.

## Limites

Files de laboratoire dédiées, un seul gestionnaire MQ ; les messages PAYMENT.DLQ/BACKOUT existants ne sont pas rejoués. L'inspection est une phase du test, pas une commande dry-run autonome. Aucune panne pendant commit testée. L'absence de source après commit ne démontre pas une idempotence métier face à deux publications du même paiement. Pas de validation Native HA.

Prochain lot : persistance métier dédiée, détection durable des doublons, conflits de contenu pour un même identifiant, puis reprise après interruption entre commit métier et acquittement MQ.

# Preuve CRC : DLQ applicative — 11 septembre 2026

Source : sortie du terminal fournie par l'utilisateur après deploy.sh et verify-dlq.sh. Aucun accès direct de l'assistant au cluster. Commit exact du clone non affiché dans cet extrait ; code DLQ livré jusqu'au commit 7b4e790.

Image publiée :
`image-registry.openshift-image-registry.svc:5000/mayabank-mq-build/payments@sha256:12031aebad96d73f79c90d7e0fe3da272ce0b5d8607e5790a92ba3bafc4f6b16`

Déploiements MQ et processeur réussis ; contrôle HTP réussi sans correction. Mauvais mot de passe rejeté. Paiement initial validé : 880cfce5-a638-4b79-abbd-4c490eb491f3.

```text
DELIVERY_FAILED reason=2085 destination=LAB.MISSING.9d6923b0b81546e485dbfeaaf73ecffc probeId=9d6923b0-b815-46e4-85db-feaaf73ecffc
DLQ_WRITTEN origin=application probeId=9d6923b0-b815-46e4-85db-feaaf73ecffc
PASS: application DLQ MQDLH reason=2085 and payload verified without consuming; probeId=9d6923b0-b815-46e4-85db-feaaf73ecffc
ACCEPTED paymentId=b15afcbc-e510-4c58-8d24-60e940381355
PASS: authenticated JMS request/reply paymentId=b15afcbc-e510-4c58-8d24-60e940381355
SIMULATED_PROCESSED paymentId=b15afcbc-e510-4c58-8d24-60e940381355
PASS lot DLQ applicative : MQDLH 2085 vérifié puis paiement valide.
```

## Portée

Erreur MQOPEN 2085 réellement observée sur destination inexistante. L'application de test publie ensuite explicitement le message persistant en PAYMENT.DLQ avec MQDLH. Le test valide en consultation non destructive le motif, la destination, l'identifiant et le contenu ; un paiement JMS valide réussit après cette opération.

Le message demeure en DLQ. Le scénario ne valide ni routage automatique par canal MQ, ni absence de perte entre MQOPEN et commit DLQ, ni rejeu, ni idempotence durable, ni Native HA. Le corps de ce message est une sonde DLQ-PROBE, pas un contrat de paiement : ne pas le rejouer aveuglément dans PAYMENT.REQUEST.Q.

Prochaine étape : rejeu contrôlé sur un scénario dédié, avec destination autorisée, sélection précise, traçabilité et vérification du contenu reçu.

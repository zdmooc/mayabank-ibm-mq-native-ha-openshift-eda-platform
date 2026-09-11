# Preuve CRC : retry et backout — 11 septembre 2026

Source : sortie du terminal fournie par l'utilisateur, après deploy.sh puis verify-backout.sh. Exécution sur son CRC, pas par l'assistant.

Commit récupéré avant l'exécution : d05c1ad.
Image construite et publiée :
`image-registry.openshift-image-registry.svc:5000/mayabank-mq-build/payments@sha256:302c71fa2b8270dbb77d28979da2d2ad840537ec187eb2f86e44fa2a412ddecc`

## Résultats

- Redémarrage MQ et déploiement du processeur réussis.
- Base HTP/OAM déjà correcte ; contrôle de l'ordre actif réussi.
- Mauvais mot de passe rejeté (2035).
- Paiement initial réussi : 635c94d7-503d-47da-8736-09afadc63543.

```text
INVALID_SENT correlationId=b8572386-24b7-436b-8b53-f54fed398f62
PASS: backout observed without consuming; deliveryCount=3; no pending request or success response; correlationId=b8572386-24b7-436b-8b53-f54fed398f62
RETRY correlationId=b8572386-24b7-436b-8b53-f54fed398f62 deliveryCount=1
RETRY correlationId=b8572386-24b7-436b-8b53-f54fed398f62 deliveryCount=2
BACKOUT correlationId=b8572386-24b7-436b-8b53-f54fed398f62 deliveryCount=3
ACCEPTED paymentId=0db22874-d82c-486c-8e70-c7a13f6e20ed
PASS: authenticated JMS request/reply paymentId=0db22874-d82c-486c-8e70-c7a13f6e20ed
SIMULATED_PROCESSED paymentId=0db22874-d82c-486c-8e70-c7a13f6e20ed
PASS lot backout : message invalide isolé puis paiement valide.
```

Les traces RETRY/BACKOUT apparaissent deux fois dans la sortie complète : le script affiche d'abord les traces vérifiées puis les logs du processeur. Cela n'atteste pas de six livraisons ni d'un double traitement.

## Portée de la preuve

Deux rollbacks avec nouvelles tentatives, puis mise en backout applicative à la troisième livraison. Le test a contrôlé le contenu, la provenance applicative et le compteur du message par consultation non destructive. Aucune demande ni réponse de succès portant cette corrélation n'était visible lors du contrôle. Un paiement valide a ensuite réussi.

Le message de preuve demeure en BACKOUT. Aucune purge effectuée. Test local mono-instance ; ne valide ni DLQ, ni rejeu, ni idempotence durable, ni TLS, ni Native HA, ni installation sur stockage neuf.

Prochaine étape : scénario DLQ contrôlé et distinct du backout, puis rejeu et idempotence durable.

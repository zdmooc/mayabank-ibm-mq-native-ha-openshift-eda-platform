# Matrice de preuves

## Statuts

- Prévu : objectif retenu.
- Documenté : conception/procédure présente.
- Implémenté : code/configuration présent.
- Testé : essai exécuté avec résultat et contexte.
- Validé : critères de sortie satisfaits et limites explicites.

Un lot peut avoir sa procédure documentée sans que sa capacité technique soit implémentée.

| Capacité | État initial | Preuve actuelle |
|---|---|---|
| Cadrage et architecture | Documenté | Lot 01 |
| Qualification du CRC actuel | Prévu | Aucune |
| MQ mono-instance sur CRC | Prévu | Aucune |
| Paiement bout en bout nouveau dépôt | Prévu | Aucune |
| Idempotence et poison/backout | Prévu | Aucune |
| Sécurité et rotation | Prévu | Aucune |
| GitOps/CI/CD | Prévu | Aucune |
| Observabilité | Prévu | Aucune |
| Native HA GCP | Prévu | Aucune |
| Perte de worker / reconnexion | Prévu | Aucune |
| PRA/restauration | Prévu, périmètre à décider | Aucune |
| Pont MQ→Kafka | Prévu | Aucune |

## Fiche à remplir pour chaque essai

- Identifiant du test ; date/heure UTC ; opérateur.
- Commit du dépôt, versions, environnement et topologie.
- Prérequis, hypothèses et critères de succès avant exécution.
- Commandes exactes expurgées et injection de panne.
- Résultats observés, logs, métriques et horodatages.
- Verdict : PASS / FAIL / BLOCKED, anomalies et limites.
- Nettoyage et ressources conservées.

## Bascule Native HA

Mesurer depuis la panne jusqu'au rétablissement du traitement applicatif, pas seulement jusqu'au pod Ready.
Compter les messages persistants confirmés avant panne, les paiements uniques traités, doublons, erreurs et messages restant en queue.
Distinguer publication incertaine, reprise réseau, reconnexion du client et reprise métier.
Tester séparément panne de pod actif, perte d'un worker, resynchronisation et fenêtre de crash du consommateur.
Aucun test destructif sur une ressource hors POC.
Une bascule HA n'est pas une preuve de PRA inter-région.

## CV

Avant tests : projet personnel en cours ; architecture documentée, validation à venir.
Après tests : citer uniquement les profils et capacités exécutés, avec lien vers preuves.
Pas de mention production, RPO nul ou RTO garanti sans protocole et résultats qui la justifient.

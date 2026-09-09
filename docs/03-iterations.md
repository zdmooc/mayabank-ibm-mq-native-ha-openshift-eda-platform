# Plan d'itérations exécutables

Le lot 01 est documentaire. Les autres lots sont prévus et ne sont pas annoncés comme livrés.

| Lot | Livrable | Critère de sortie |
|---|---|---|
| 01 | Cadrage, architecture, prérequis, plan, preuves | Documentation publiée ; collecte CRC encore à réaliser |
| 02 | Qualification CRC, ADR versions/runtime/transactions | Compatibilité et ressources confirmées ; choix justifiés |
| 03 | MQ mono-instance local, volumes et objets MQ | Déploiement reproductible ; messages persistants envoyés/reçus ; recovery testé |
| 04 | Applications paiement et base | Parcours bout en bout ; identifiants et statuts cohérents |
| 05 | Backout, poison, retry, idempotence | Doublons, concurrence et fenêtres de crash testés ; résultats enregistrés |
| 06 | Sécurité MQ/API et Vault | Tests positifs et négatifs d'accès ; rotation testée ; aucun secret Git |
| 07 | Argo CD et Tekton | Build/test/scan/image ; déploiement GitOps et changement contrôlé |
| 08 | Observabilité et opérations Ansible | Alertes déclenchées, dashboards et procédures exécutés |
| 09 | IaC GCP et préparation Native HA | Plan relu, devis régional, droits/licences et destruction validés ; sans apply |
| 10 | Campagne GCP autorisée | Bascules pod/nœud et reconnexion mesurées ; données vérifiées ; ressources nettoyées |
| 11 | Extensions EDA/API | Pont MQ→Kafka et gouvernance testés ; AMQ comparé ; périmètre explicite |
| 12 | Dossier final et démonstration CV | Synthèse conforme aux preuves ; limites et coûts réels publiés |

## Contenu minimal de chaque lot technique

- Explication de l'architecture et des changements.
- Fichiers complets, dépendances épinglées et prérequis.
- Commandes de build/déploiement, vérification et nettoyage ciblé.
- Tests automatiques lorsque possibles.
- Résultats attendus séparés des résultats observés.
- Runbook d'échec et reprise du lot.
- Preuves expurgées, environnement et commit associés.

## Réutilisation

Les cas métier MQ et les scénarios d'erreur viennent des phases 05/13.
Ne pas importer tout formation-was : pas d'archives, d'artefacts compilés, de données personnelles ou de fichiers d'environnement.
L'assessment fournit les risques et tests, pas la preuve de migration.

## Validation finale

Le dépôt doit pouvoir être repris par une autre personne.
La commande de suppression devra être limitée aux ressources identifiées du POC et précédée d'une sauvegarde des preuves.
La compatibilité réelle et les éventuels blockers sont documentés sans inventer un succès.

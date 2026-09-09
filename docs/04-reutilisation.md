# Réutilisation des travaux existants

## Références inspectées

- formation-was : commit cac6b41b555ec313d29c387b72c95eddb27d903d.
- assessment-was-openshift : commit f8e48edf7536aaede8e59c8df030338a45707725.

Ces références correspondent à la revue initiale, pas à des tests rejoués dans ce dépôt.

## Ce qui existe

| Source | Réutilisation | Réserve |
|---|---|---|
| Phase 05 MQ | Objets MQ, clients JMS, diagnostics, scénarios rollback | Adapter chemins Windows/configuration ; ne pas copier les secrets de lab |
| Journal phase05-mq-validation.log | Preuve historique de QM.PHASE05 actif sous Windows | Ne prouve ni OpenShift ni Native HA |
| Phase 13 | Modèle paiement, MDB/EJB, PostgreSQL, poison/backout | Requalifier transactions, idempotence et comportement de reprise |
| Assessment | Scoring, backlog, vagues, risques JTA/MDB | Diagnostic de migration, pas migration accomplie |
| OpenRewrite | Patchs dry-run existants | Non appliqués ; revue requise |

## Points à traiter dans le nouveau code

1. Phase 05 PaymentMessageBean : NOT_SUPPORTED + JDBC autocommit, avec log parlant de commit JMS/JDBC. Ne pas reprendre cette formulation comme garantie transactionnelle.
2. Phase 13 PaymentProcessingServiceBean : persistance d'événement sans traitement explicite du doublon dans la méthode inspectée. Concevoir une stratégie durable et la tester.
3. Configuration MQ : durcir les canaux et les droits ; ne pas déduire mTLS d'un seul attribut affiché.
4. Validation phase 13 : résultats attendus dans la fiche, à distinguer des résultats réellement collectés.
5. Pipeline GitOps, Native HA, Vault et infrastructure GCP : à construire dans ce dépôt.

## Parcours valorisable

Laboratoire WAS/MQ existant → assessment de migration → portage local OpenShift → industrialisation → validation Native HA GCP.

Les anciens dépôts restent inchangés. Toute reprise de code doit citer sa provenance et son commit ; les défauts relevés sont corrigés dans les nouveaux modules avec tests.

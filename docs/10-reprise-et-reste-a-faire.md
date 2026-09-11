# Reprise du POC — état au 11 septembre 2026

Point de pause demandé par Zidane. Reprendre ici à la prochaine séance. Aucun déploiement GCP à lancer pendant la pause.

## 1. État validé

- [x] IBM MQ Developer 9.4.5.1 mono-instance sur OpenShift Local/CRC.
- [x] Files persistantes PAYMENT.REQUEST.Q, PAYMENT.RESPONSE.Q, PAYMENT.BACKOUT.Q et PAYMENT.DLQ créées.
- [x] Publication/lecture locale par bindings.
- [x] Message retrouvé après redémarrage contrôlé du pod MQ.
- [x] Construction Java par BuildConfig OpenShift et image publiée dans le registre interne.
- [x] Connexion JMS réseau interne authentifiée, mauvais mot de passe rejeté.
- [x] Paiement simulé : publication, consommation, traitement, réponse corrélée.
- [x] Reconnexion du processeur et nouveau paiement après redémarrage MQ, DEBUG retiré.
- [x] Script ensure-htp-order.sh exécuté sur configuration déjà corrigée : aucune modification ni demande de redémarrage, puis nouveau PASS JMS.

Dernier paiement validé : `821845f3-f72f-411f-a72e-e2c53be9ab7f`.
Dernière image publiée : `sha256:95fed63b98f7e54cda973acca051ac071e380e6c07b5b6dd352dea6c80ac1894`. Le dernier extrait ne donne pas le commit exact du clone.
Preuves : [MQ local](../evidence/2026-09-09-crc-mq.md), [JMS et reprise](../evidence/2026-09-10-crc-jms-request-reply.md).

**Non validés : installation complète sur volume neuf, Native HA, TLS/mTLS, idempotence durable, DLQ automatique par canal, charge et PRA.**
Retry/backout validés : [preuve du 11 septembre](../evidence/2026-09-11-crc-retry-backout.md). DLQ applicative validée : [preuve](../evidence/2026-09-11-crc-dlq-applicative.md). Le placement automatique par un canal reste à tester.
Le BuildConfig actuel n'est pas un pipeline Tekton. Le déploiement actuel utilise des scripts oc, pas Argo CD.

## 2. Environnement à conserver

- Windows / Git Bash, clone sous `$HOME/workspaces/mayabank-ibm-mq-native-ha-openshift-eda-platform`.
- CRC 2.63.0, OpenShift 4.22.7, un nœud crc, 8 CPU et 24 Gio de RAM configurés.
- Namespace MQ et applications : `mayabank-mq-local`.
- Namespace de construction : `mayabank-mq-build`.
- Gestionnaire : `QM.MAYABANK`, Deployment : `mq`, processeur : `payment-processing`.
- PVC : `mq-data`, demande 5 Gi ; capacité affichée 79 Gi liée au stockage hostpath partagé. Ne pas l'interpréter comme 79 Gi réservés.
- Le POC `wero-poc` partage CRC : ne pas modifier ses ressources.
- Secrets conservés dans Kubernetes ; ne jamais les copier dans le dépôt ou les sorties de diagnostic.
- Ne pas supprimer CRC ou le PVC pour recommencer. Le test sur stockage neuf devra être isolé.
- Ne pas réappliquer `scripts/local-crc/deploy.sh` : il correspond au profil initial avant le lot paiement.

Les versions et capacités ci-dessus décrivent l'environnement observé ; elles ne constituent pas une confirmation de support éditeur.

## 3. Première action à la reprise

Depuis Git Bash, vérifier le cluster et les ressources :

```bash
cd "$HOME/workspaces/mayabank-ibm-mq-native-ha-openshift-eda-platform" &&
git pull --ff-only &&
eval "$(crc oc-env)"
crc status
oc whoami
oc get nodes
oc get clusteroperators
oc adm top nodes
oc -n mayabank-mq-local get pods,pvc
```

Si CRC est arrêté, le démarrer avant les commandes oc. Si l'authentification a expiré, rétablir la connexion localement sans publier de jeton.
Lorsque le cluster est disponible :

```bash
bash scripts/payments/ensure-htp-order.sh &&
bash scripts/payments/verify.sh
```

Attendre le PASS du paiement avant de développer le lot suivant. Les options strictes restent dans les scripts ; ne pas coller `set -eu` dans le shell interactif.
Ne pas relancer le build complet pour ce simple contrôle.

## 4. Retry/backout, DLQ applicative et rejeu validés ; prochain lot idempotence

- [x] Message invalide avec corrélation unique.
- [x] Deux retries observés ; backout applicatif à la troisième livraison.
- [x] Consultation non destructive du message rejeté et contrôle de ses propriétés.
- [x] Aucune demande ni réponse de succès correspondante visible lors du contrôle.
- [x] Paiement valide après le rejet.
- [x] Code, guide et preuve publiés.

Guide : [retry/backout](11-test-retry-backout.md). Preuve : [exécution CRC](../evidence/2026-09-11-crc-retry-backout.md).

- [x] Erreur MQOPEN 2085 sur destination inexistante.
- [x] Publication applicative en DLQ et contrôle MQDLH/contenu non destructif.
- [x] Paiement valide après le scénario DLQ.
- [x] Preuve DLQ enregistrée.

Rejeu contrôlé validé : [preuve CRC](../evidence/2026-09-11-crc-rejeu-controle.md).

- [x] Sonde et files de rejeu dédiées.
- [x] Sélection précise, validation MQDLH et destination autorisée.
- [x] Phase d'inspection non destructive ; transaction source/cible/audit.
- [x] Échec 2051 et rollback conservant le message source original.
- [x] Transfert réussi, cible/audit vérifiés, source absente et second GET=2033.
- [x] Paiement valide après le scénario.
- [ ] Mode dry-run autonome et outil de rejeu d'exploitation : non livrés.

Prochain lot : persistance métier et idempotence durable.

- [ ] Vérifier les ressources CRC avant d'ajouter une base dédiée, sans utiliser la base Wero.
- [ ] Définir une clé métier unique et le comportement en cas de même identifiant avec contenu différent.
- [ ] Conserver le résultat métier et la trace de traitement durablement.
- [ ] Publier deux fois le même paiement : vérifier un seul effet métier.
- [ ] Redémarrer le processeur et vérifier que la détection de doublon persiste.
- [ ] Tester l'interruption entre commit métier et acquittement MQ ; documenter les transactions et limites.

Le message DLQ existant contient une sonde DLQ-PROBE, pas un paiement : ne pas le réinjecter aveuglément dans PAYMENT.REQUEST.Q. Conserver les preuves existantes.

## 5. Checklist restante pour terminer le périmètre

### A. Reproductibilité et diagnostics

- [ ] Tester la branche de réparation HTP sur MQ avec une base OAM seule ; actuellement testée sur fichiers simulés, le correctif manuel a fonctionné sur CRC.
- [ ] Tester l'installation complète dans un environnement isolé avec stockage neuf, sans effacer le PVC validé.
- [ ] Améliorer les erreurs JMS : opération concernée, cause liée, code MQ et horodatage, sans secrets.
- [ ] Rendre l'état de disponibilité du processeur représentatif de sa connexion JMS.
- [ ] Limiter les logs de vérification à l'exécution courante pour ne pas confondre anciens échecs et nouvel incident.
- [ ] Vérifier les versions, épingler les dépendances et documenter le cycle de mise à jour.

### B. Fiabilité métier

- [x] Lot retry/backout terminé et testé sur CRC.
- [x] DLQ applicative testée ; différence avec backout documentée.
- [ ] Tester séparément une DLQ automatique via canal MQ.
- [x] Rejeu contrôlé avec audit validé sur files dédiées (outil générique non livré).
- [ ] Ajouter une persistance métier dédiée et une idempotence durable.
- [ ] Tester doublons, interruption entre traitement et acquittement, reprise et cohérence transactionnelle.
- [ ] Documenter les garanties réellement obtenues, sans promesse « exactly once » non démontrée.

### C. Sécurité et exposition

- [ ] TLS/mTLS MQ : certificats, validation du serveur, rotation et tests de refus.
- [ ] Séparer les identités producteur/consommateur et restreindre les droits par file.
- [ ] Intégrer Vault avec une stratégie explicite de renouvellement des secrets.
- [ ] Ajouter l'API de paiement et sa protection OAuth2/JWT, quotas et rate limiting à la couche appropriée.
- [ ] Compléter RBAC, quotas, limites et tests NetworkPolicy.
- [ ] Documenter les différences entre l'authentification Developer locale et la cible cloud.

### D. Industrialisation

- [ ] Pipeline Tekton : compilation, tests, construction, analyse et publication d'image.
- [ ] Argo CD : déploiement déclaratif, dérive, promotion et rollback.
- [ ] Séparer les configurations des environnements locaux et cloud.
- [ ] Terraform pour le socle GCP, état distant protégé et gestion du cycle de vie.
- [ ] Ansible pour les opérations documentées et répétables.
- [ ] Vérifier une installation reproductible depuis le dépôt, puis documenter le retour arrière.

### E. Observabilité, capacité et exploitation

- [ ] Métriques MQ et applicatives, Prometheus/Grafana, dashboards et alertes.
- [ ] Profondeur et âge des messages, débit, erreurs, backout/DLQ, connexions et stockage.
- [ ] Tests de charge avec taille de message, débit cible, latences et ressources observées.
- [ ] Capacity planning et seuils d'alerte fondés sur les mesures.
- [ ] Runbooks : arrêt MQ, connexion refusée, DNS, file bloquée, stockage saturé, certificats et rejeu.

### F. Architecture et gouvernance

- [ ] Finaliser HLD/LLD, flux et ADR à partir de la solution réellement déployée.
- [ ] Gouvernance EDA : naming, ownership, contrats, versioning, compatibilité et cycle de vie.
- [ ] Documenter sauvegarde/restauration et PRA ; distinguer objectifs RTO/RPO et valeurs mesurées.
- [ ] Préparer le transfert de compétences et une démonstration reproductible.
- [ ] Relier les exigences de la mission aux livrables et preuves.

### G. GCP et Native HA — après stabilisation locale

- [ ] Vérifier versions compatibles, opérateur, licences et droits d'utilisation.
- [ ] Établir une estimation FinOps datée, incluant cluster, disques, réseau, exposition et licences.
- [ ] Faire valider le coût et le lancement cloud avant toute création payante.
- [ ] Déployer OpenShift multi-nœuds via une procédure documentée, avec trois instances MQ réparties sur des workers distincts.
- [ ] Configurer stockage, placement, réseau et sécurité pour le profil Native HA retenu.
- [ ] Tester perte de pod puis perte de worker et reconnexion des clients.
- [ ] Mesurer interruption, reprise et comportement des messages ; enregistrer les preuves.
- [ ] Tester sauvegarde/restauration séparément de la haute disponibilité.
- [ ] Détruire les ressources de démonstration selon le plan et contrôler les coûts résiduels.

### H. Extensions facultatives

- [ ] Pont MQ/Kafka ou variante documentée si utile à la mission.
- [ ] Comparaison avec Red Hat AMQ.
- [ ] Anti-fraude simulée.
- [ ] Ne pas retarder la validation du socle MQ pour ces extensions.

## 6. Critères de clôture

Le POC local sera présentable quand le parcours valide, les erreurs, la reprise, l'idempotence, la sécurité, l'industrialisation et l'observabilité prévus auront leurs preuves et leurs guides.
Le volet Native HA ne pourra être déclaré réalisé qu'après exécution et mesure sur le cluster multi-nœuds.
Le CV devra distinguer « réalisé et testé », « documenté » et « prévu ».

## 7. Message pour reprendre avec l'assistant

> Reprends ce dépôt en lisant docs/10-reprise-et-reste-a-faire.md, le README et les preuves. Le paiement JMS authentifié et sa reprise après redémarrage sont validés sur CRC. Le contrôle HTP passe sans modification. Le lot retry/backout est aussi validé (preuve du 11 septembre). La DLQ applicative avec MQDLH 2085 est validée aussi. Le rejeu contrôlé sur files dédiées est validé aussi (rollback 2051 et commit avec audit). Commence par la persistance métier et l'idempotence durable. Ne rejoue pas la sonde DLQ-PROBE comme un paiement. Préserve Wero, le PVC et les secrets. Publie une étape vérifiée dans le dépôt avec les commandes Git Bash à exécuter ; n'annonce pas de test CRC ou Native HA réussi sans sortie réelle.

# Reprise du POC — état au 10 septembre 2026

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

Dernier paiement validé : `ff2f23cd-0acf-402e-88de-40cb6934e90b`.
Commit exécuté par l'utilisateur : `a27fa6a`. Les commits suivants ont enrichi la documentation.
Preuves : [MQ local](../evidence/2026-09-09-crc-mq.md), [JMS et reprise](../evidence/2026-09-10-crc-jms-request-reply.md).

**Non validés : installation complète sur volume neuf, Native HA, TLS/mTLS, idempotence durable, retry/backout/DLQ, charge et PRA.**
La présence des files BACKOUT/DLQ n'est pas une preuve de leur fonctionnement.
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

## 4. Prochain lot précis : message invalide et backout

À implémenter et tester à la prochaine séance, sans déclarer ces essais déjà réussis :

- [ ] Relire PaymentProcessing.java, PaymentOrder.java, les droits MQ et les scripts actuels.
- [ ] Ajouter un scénario qui publie un message volontairement invalide avec un identifiant unique.
- [ ] Vérifier les tentatives et rollbacks via des logs corrélés et le compteur de livraison.
- [ ] Distinguer le traitement applicatif du mécanisme automatique éventuel du fournisseur JMS ; vérifier le nombre réellement observé, sans supposer que BOTHRESH déplace seul le message.
- [ ] Vérifier que le message identifié arrive dans PAYMENT.BACKOUT.Q, avec lecture non destructive si possible et droits minimaux nécessaires.
- [ ] Vérifier qu'il n'est plus en boucle sur REQUEST et qu'aucune réponse de succès n'a été produite pour ce message.
- [ ] Publier ensuite un paiement valide et obtenir son PASS request/reply.
- [ ] Livrer le script de test, les changements applicatifs nécessaires et le guide d'exécution.
- [ ] Enregistrer les résultats réels dans evidence/, avec identifiants et limites.

Ne pas purger les files pour faire réussir le test. Ne pas accorder des droits globaux à app. Le scénario DLQ est distinct du scénario backout.

## 5. Checklist restante pour terminer le périmètre

### A. Reproductibilité et diagnostics

- [ ] Tester la branche de réparation HTP sur MQ avec une base OAM seule ; actuellement testée sur fichiers simulés, le correctif manuel a fonctionné sur CRC.
- [ ] Tester l'installation complète dans un environnement isolé avec stockage neuf, sans effacer le PVC validé.
- [ ] Améliorer les erreurs JMS : opération concernée, cause liée, code MQ et horodatage, sans secrets.
- [ ] Rendre l'état de disponibilité du processeur représentatif de sa connexion JMS.
- [ ] Limiter les logs de vérification à l'exécution courante pour ne pas confondre anciens échecs et nouvel incident.
- [ ] Vérifier les versions, épingler les dépendances et documenter le cycle de mise à jour.

### B. Fiabilité métier

- [ ] Terminer le lot backout décrit ci-dessus.
- [ ] Ajouter un scénario DLQ contrôlé et expliquer la différence avec backout.
- [ ] Implémenter le rejeu contrôlé avec traçabilité.
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

> Reprends ce dépôt en lisant docs/10-reprise-et-reste-a-faire.md, le README et les preuves. Le paiement JMS authentifié et sa reprise après redémarrage sont validés sur CRC. Le contrôle HTP passe sans modification. Commence par le lot message invalide/retry/backout, puis un paiement valide. Préserve Wero, le PVC et les secrets. Publie une étape vérifiée dans le dépôt avec les commandes Git Bash à exécuter ; n'annonce pas de test CRC ou Native HA réussi sans sortie réelle.

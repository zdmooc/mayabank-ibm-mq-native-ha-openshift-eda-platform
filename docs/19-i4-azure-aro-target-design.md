# I4 — Design cible Azure Red Hat OpenShift / IBM MQ Native HA

## Statut

**DESIGN_COMPLETE — AZURE IMPLEMENTATION PENDING — RUNTIME PENDING**

Ce document fixe la cible d'architecture pour l'implémentation future sur Azure. Il ne constitue pas une preuve d'exécution. Aucune ressource Azure n'est créée par ce document et aucun secret/licence n'est activé.

## 1. Objectif

Déployer un queue manager IBM MQ Native HA sur Azure Red Hat OpenShift (ARO) avec :

- trois instances Native HA réparties sur trois workers distincts ;
- répartition des workers sur trois Availability Zones quand la région ARO choisie le permet ;
- stockage bloc RWO indépendant par instance ;
- réplication Native HA chiffrée ;
- accès applicatif mTLS, CHLAUTH et OAM least privilege ;
- intégration GitOps ;
- observabilité Prometheus/Grafana ;
- tests de panne pod, worker et zone ;
- conservation des preuves d'exécution ;
- séparation stricte entre HA intra-région et PRA inter-région.

## 2. Principes d'architecture

1. Native HA reste la couche de haute disponibilité du queue manager ; Azure ne remplace pas la réplication MQ.
2. Chaque instance MQ possède son propre volume bloc RWO. Aucun stockage RWX partagé n'est requis pour Native HA.
3. Les trois instances doivent être placées sur trois workers distincts ; la cible Azure cherche aussi trois zones distinctes.
4. Le StatefulSet et les ressources générées par IBM MQ Operator ne sont jamais modifiés manuellement.
5. L'accès MQ applicatif reste privé ; aucune Route publique MQ n'est nécessaire.
6. Les secrets, certificats et pull secrets restent hors Git.
7. La licence reste `accept: false` tant que l'entitlement n'a pas été vérifié.
8. Une architecture `DESIGNED` ne devient `RUNTIME_VALIDATED` qu'après exécution et conservation des preuves.

## 3. Architecture logique

```text
                   Utilisateurs / SI bancaire / CI-CD
                               |
                               v
                    Hub réseau Azure / Firewall
                               |
                     ExpressRoute / VPN / VNet
                               |
                     Spoke ARO privé multi-AZ
                               |
       +-----------------------+-----------------------+
       |                       |                       |
       v                       v                       v
  Worker MQ AZ1           Worker MQ AZ2           Worker MQ AZ3
       |                       |                       |
  MQ Native HA             MQ Native HA             MQ Native HA
   Active/Replica            Replica/Active           Replica/Active
       |                       |                       |
 Azure Disk RWO 1         Azure Disk RWO 2         Azure Disk RWO 3
       \_______________________|_______________________/
                     Réplication MQ Native HA
                             TLS

 Applications payment-order / payment-processing
              |                         |
              +------ mTLS JMS ---------+
                         |
                    Service MQ
                         |
                    QM.PROD

 Observabilité :
 MQ built-in metrics + mq_prometheus -> ServiceMonitor -> UWM Prometheus -> Grafana/Alertmanager
```

## 4. Cible Azure

### 4.1 Service OpenShift

Service cible : **Azure Red Hat OpenShift (ARO)**.

Hypothèses de design :

- cluster ARO privé ;
- région Azure supportant Availability Zones ;
- au moins trois workers pour les workloads MQ ;
- trois control-plane nodes gérés par le service ARO ;
- pull secret Red Hat configuré pour l'accès OperatorHub ;
- IBM MQ Operator installé après vérification de la matrice de compatibilité ARO/OpenShift/MQ.

La version ARO exacte sera figée seulement au moment de l'implémentation. La version disponible en 2026 ne doit pas être utilisée comme garantie de compatibilité future avec IBM MQ Operator.

### 4.2 Répartition AZ

Cible : un worker MQ dans chacune des trois zones de disponibilité.

```text
AZ1                     AZ2                     AZ3
+----------------+      +----------------+      +----------------+
| worker-mq-az1  |      | worker-mq-az2  |      | worker-mq-az3  |
| MQ instance 0  |      | MQ instance 1  |      | MQ instance 2  |
+----------------+      +----------------+      +----------------+
| Azure Disk RWO |      | Azure Disk RWO |      | Azure Disk RWO |
+----------------+      +----------------+      +----------------+
```

Au moment de l'implémentation :

- vérifier les MachineSets créés par ARO ;
- créer si nécessaire des MachineSets dédiés MQ par zone ;
- appliquer des labels dédiés aux workers MQ ;
- utiliser uniquement les mécanismes de scheduling supportés par IBM MQ Operator ;
- vérifier après déploiement que les trois pods sont effectivement sur trois nodes et trois zones distincts.

Aucune hypothèse de placement n'est considérée validée avant `oc get pods -o wide` et inspection des labels de zone.

## 5. Compute

Le dimensionnement final dépend du benchmark MQ. Le design prévoit :

- un pool de workers dédié ou isolable pour MQ ;
- au minimum trois workers schedulables ;
- CPU et RAM réservés pour éviter la contention avec les workloads applicatifs ;
- aucune surallocation agressive pour les ressources MQ critiques ;
- quotas et LimitRanges au niveau namespace.

Baseline POC Azure : choisir une VM Azure de gamme généraliste ou mémoire optimisée compatible ARO, puis mesurer CPU, mémoire, latence disque et débit MQ avant de retenir la taille de production.

Le SKU exact n'est pas figé dans Git avant mesure de charge et contrôle de quota/coût.

## 6. Stockage

Native HA utilise trois stockages indépendants.

Cible Azure :

- Azure Disk CSI ;
- mode `ReadWriteOnce` ;
- disque Premium adapté aux IOPS/latence attendues ;
- provisioning dynamique ;
- binding topology-aware, idéalement `WaitForFirstConsumer` ;
- un PVC par instance Native HA ;
- `deleteClaim: false` pour éviter la suppression automatique des données lors des opérations de lab ;
- chiffrement au repos Azure activé ;
- option CMK à étudier séparément si exigée par la politique sécurité.

Le StorageClass réel sera choisi après inspection du cluster ARO. Le manifeste générique ne doit pas supposer un nom de StorageClass Azure avant cette étape.

## 7. Réseau

### 7.1 Topologie

Design cible : hub-and-spoke Azure.

- Hub : Azure Firewall/NVA, DNS, connectivité entreprise, ExpressRoute/VPN.
- Spoke ARO : subnets control plane/workers selon le modèle ARO.
- API ARO privée.
- Ingress privé pour les applications internes.
- MQ non publié sur Internet.
- Sorties Internet contrôlées pour registries et dépendances autorisées.

### 7.2 Flux autorisés

| Source | Destination | Port/protocole | Usage |
|---|---|---:|---|
| payment-order | MQ Service | 1414/TCP | JMS mTLS |
| payment-processing | MQ Service | 1414/TCP | JMS mTLS |
| instances Native HA | instances Native HA | selon configuration Operator | réplication Native HA |
| UWM Prometheus | MQ metrics/exporter | 9157/TCP ou endpoint Operator | scrape |
| workloads | DNS OpenShift | DNS | résolution interne |
| GitOps/Operator | registries approuvées | HTTPS | images/operators |

Les NetworkPolicies restent deny-by-default avec exceptions explicites.

## 8. Sécurité

### 8.1 Identités MQ

Identités séparées :

- `paymentorder` ;
- `paymentproc` ;
- `mqmonitor` pour l'observabilité ;
- comptes d'administration hors chemin applicatif.

Le compte générique CRC `app` ne fait pas partie de la cible production.

### 8.2 Channels

Channels cibles :

- `PAY.ORDER.SVRCONN` ;
- `PAY.PROC.SVRCONN`.

Exigences :

- mTLS obligatoire ;
- TLS 1.3 ou politique approuvée par la plateforme ;
- mapping certificat -> identité MQ via CHLAUTH ;
- deny générique avant mappings explicites ;
- OAM strictement aligné sur le rôle producteur/consommateur.

### 8.3 Secrets et PKI

Cible Azure : Azure Key Vault comme coffre de référence, avec mécanisme approuvé de synchronisation vers des Secrets Kubernetes/OpenShift.

Secrets attendus :

- certificat de réplication Native HA ;
- certificat serveur MQ ;
- CA clients ;
- certificat `payment-order` ;
- certificat `payment-processing` ;
- certificat/identité `mqmonitor` si nécessaire ;
- pull secret IBM/Red Hat selon entitlement.

Règles :

- aucune clé privée dans Git ;
- rotation documentée ;
- permissions Key Vault minimales ;
- Managed Identity Azure privilégiée pour l'accès aux services Azure ;
- secret materialisé dans le cluster uniquement lorsque requis par l'Operator ou le workload.

## 9. IBM MQ Native HA

Le manifeste générique reste `deploy/native-ha/qm-prod.yaml`.

Cible :

- `QueueManager` IBM MQ Operator ;
- `availability.type: NativeHA` ;
- trois instances ;
- réplication TLS ;
- stockage persistant ;
- ServiceMonitor activé ;
- console web désactivée ;
- route MQ externe désactivée.

La valeur de version MQ est une cible à revalider avant Azure. Le manifeste n'est pas autorisé à activer la licence automatiquement.

## 10. Observabilité

### 10.1 Pipeline

```text
IBM MQ
  |-- metrics Operator/built-in
  |-- mq_prometheus dédié si nécessaire pour les métriques queue-level
             |
             v
      ServiceMonitor
             |
             v
 OpenShift User Workload Monitoring
             |
       +-----+-----+
       |           |
    Grafana    Alertmanager
```

### 10.2 Métriques obligatoires

- état queue manager ;
- état Native HA / replicas / synchronisation ;
- profondeur des queues ;
- âge du plus vieux message ;
- DLQ et backout ;
- taux de PUT/GET ;
- erreurs de connexion/channel ;
- CPU/mémoire ;
- capacité/latence stockage ;
- disponibilité du target Prometheus.

Le collector queue-level doit utiliser l'identité dédiée `mqmonitor` et non une identité applicative.

### 10.3 Alertes minimales

- queue manager indisponible ;
- replica désynchronisée ;
- perte de quorum ;
- DLQ > 0 ;
- BACKOUT > 0 ;
- queue depth au-dessus d'un seuil ;
- oldest message age au-dessus d'un seuil ;
- espace disque faible ;
- target Prometheus absent.

Les seuils de production seront calibrés après charge réelle.

## 11. GitOps et séparation des responsabilités

Deux couches :

### Couche Azure / plateforme

Gérée par IaC lors de l'implémentation future :

- Resource Groups ;
- VNet/Subnets/Private DNS ;
- ARO ;
- MachineSets ou capacité worker ;
- Key Vault ;
- Log/monitoring Azure si retenu ;
- budgets/tags/policies.

### Couche OpenShift / MQ

Gérée par GitOps depuis ce dépôt :

- namespace MQ ;
- IBM MQ Operator configuration spécifique au workload ;
- `QueueManager` ;
- ConfigMaps MQSC/INI ;
- NetworkPolicies ;
- ServiceMonitor/PrometheusRule ;
- dashboards ;
- workloads payment-order/payment-processing.

Argo CD ne doit pas posséder les secrets privés eux-mêmes ; il peut déployer les références ExternalSecret/SecretProvider selon le mécanisme retenu.

## 12. Résilience et scénarios de validation Azure

### Test A — nominal

- 3 instances MQ ;
- une Active, deux Replica ;
- 3/3 Ready ;
- replicas `INSYNC(Yes)` ;
- paiement JMS de bout en bout ;
- idempotence PostgreSQL ;
- métriques visibles.

### Test B — suppression du pod actif

- flux paiement continu ;
- suppression du pod actif uniquement ;
- mesure T0 -> nouvelle Active ;
- mesure reconnexion JMS ;
- aucun double effet métier ;
- retour à 3/3.

### Test C — perte du worker actif

- panne/drain contrôlé du worker dans un lab dédié ;
- bascule vers une autre instance ;
- mesure service indisponible ;
- vérification messages persistants ;
- restauration du worker ;
- retour `INSYNC`.

### Test D — perte d'une Availability Zone

Lorsque le lab Azure le permet :

- rendre indisponible la capacité d'une zone ou simuler la perte du worker de cette zone ;
- vérifier maintien du quorum 2/3 ;
- vérifier la continuité de traitement ;
- vérifier la réintégration de l'instance.

### Test E — perte de quorum

Test destructif uniquement si environnement jetable et procédure de récupération validée.

Objectif : constater le comportement sans revendiquer de disponibilité en absence de majorité.

## 13. SLO de validation POC

Ces valeurs sont des **critères de test**, pas des garanties produit :

- aucune perte de message MQ persistant déjà confirmé lors d'une panne simple pod/worker ;
- aucun double effet métier grâce à l'idempotence PostgreSQL ;
- reconnexion automatique JMS démontrée ;
- récupération du service mesurée et documentée ;
- retour du groupe à 3/3 et `INSYNC` après restauration.

Le seuil RTO chiffré de production sera fixé avant le test Azure avec le métier/exploitation. Le dépôt ne doit pas inventer un SLA.

## 14. HA et PRA

Native HA multi-AZ protège la disponibilité intra-région. Il ne remplace pas un PRA inter-région.

Cible PRA à étudier après I4 Azure :

```text
Région Azure primaire              Région Azure secondaire
ARO + Native HA                    ARO + Native HA
3 AZ                               3 AZ
        \                         /
         \--- stratégie PRA -----/
              CRR ou autre
```

IBM MQ Native HA Cross-Region Replication peut être évalué séparément selon version, entitlement, latence et exigences RPO/RTO. Aucune validation CRR n'est revendiquée dans I4.

## 15. Sauvegarde et restauration

Même avec Native HA :

- définir une stratégie de sauvegarde des configurations et données nécessaires ;
- sauvegarder Git/configurations hors secrets ;
- gérer les sauvegardes PostgreSQL séparément ;
- tester la restauration ;
- documenter ordre de reconstruction plateforme -> Operator -> secrets -> QueueManager -> applications.

La sauvegarde n'est pas remplacée par la réplication HA.

## 16. FinOps / GreenOps

Pour le lab Azure :

- tags `project`, `environment`, `owner`, `expiry` ;
- budget Azure et alertes coût ;
- taille de workers minimale compatible avec les tests ;
- arrêt/destruction du lab hors périodes de test si le modèle ARO le permet ;
- conservation des preuves de coût ;
- aucune création de ressources cloud depuis ce dépôt sans validation explicite.

Pour la production : mesurer CPU/RAM/disque/IOPS et dimensionner à partir du workload, pas à partir d'un surprovisionnement arbitraire.

## 17. Gates avant implémentation

Le déploiement Azure est bloqué tant que ces points ne sont pas validés :

- [ ] abonnement Azure et quotas ;
- [ ] région ARO avec 3 AZ ;
- [ ] version ARO/OpenShift choisie ;
- [ ] compatibilité IBM MQ Operator vérifiée ;
- [ ] entitlement IBM MQ confirmé ;
- [ ] coût estimé et accepté ;
- [ ] pull secrets disponibles hors Git ;
- [ ] stratégie Key Vault/secrets validée ;
- [ ] StorageClass Azure Disk RWO validée ;
- [ ] stratégie DNS/réseau/egress validée ;
- [ ] SLO/RTO/RPO POC définis ;
- [ ] plan de destruction du lab validé.

## 18. Critères de sortie I4 Azure

I4 Azure devient `RUNTIME_VALIDATED` uniquement si les preuves montrent :

- trois workers/instances sur trois zones distinctes ou justification documentée si la région ne le permet pas ;
- 1 Active + 2 Replica, `INSYNC` ;
- stockage RWO indépendant par instance ;
- paiement mTLS fonctionnel ;
- panne du pod actif validée ;
- panne du worker actif validée ;
- reconnexion JMS mesurée ;
- absence de perte de message confirmé ;
- absence de double effet métier ;
- observabilité/alerting opérationnels ;
- cluster revenu à l'état nominal ;
- coût et nettoyage enregistrés.

Avant ces preuves, la formulation correcte reste :

> IBM MQ Native HA on Azure Red Hat OpenShift — complete target design, Azure runtime implementation pending.

## 19. Décisions ouvertes pour l'implémentation

À trancher juste avant création du lab :

- région Azure primaire ;
- SKU des workers MQ ;
- StorageClass/tiers Azure Disk ;
- mécanisme Key Vault -> OpenShift Secret ;
- version ARO/OpenShift ;
- version IBM MQ Operator et QueueManager ;
- stratégie de monitoring Grafana ;
- seuils d'alertes ;
- cible PRA/CRR éventuelle.

Ces choix sont volontairement différés pour éviter d'inscrire dans Git des hypothèses cloud obsolètes.

## Références de conception

- Microsoft Azure Red Hat OpenShift — service definition : https://learn.microsoft.com/azure/openshift/openshift-service-definitions
- Microsoft ARO release notes : https://learn.microsoft.com/azure/openshift/azure-redhat-openshift-release-notes
- IBM MQ Native HA : https://www.ibm.com/docs/en/ibm-mq/9.4.x?topic=availability-native-ha
- IBM MQ containers / HA architecture : https://www.ibm.com/docs/en/ibm-mq/9.4.x?topic=containers

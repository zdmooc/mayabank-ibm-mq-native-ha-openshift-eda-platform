# I4 — IBM MQ Native HA sur OpenShift multi-worker

## Statut

**DESIGN_COMPLETE — IMPLEMENTATION AZURE PENDING — RUNTIME PENDING**

Le design fonctionnel et technique I4 est considéré terminé. La validation runtime Native HA sera réalisée ultérieurement sur Azure Red Hat OpenShift (ARO), pas sur CRC mono-nœud.

Documents associés :

- [Design cible Azure ARO / IBM MQ Native HA](19-i4-azure-aro-target-design.md)
- [Plan d'implémentation Azure et gates](20-i4-azure-implementation-plan.md)

## Objectif

Valider la brique qui manque encore pour la mission EDA / IBM MQ / OpenShift : un queue manager IBM MQ Native HA réellement exécuté sur trois workers distincts, avec réplication, quorum, mTLS de réplication, persistance et failover mesuré.

## Architecture cible

```text
Clients JMS
   |
   v
Service MQ / SVRCONN mTLS
   |
   v
                 QM.PROD
        +-----------------------+
        | IBM MQ Native HA      |
        | quorum 3 instances    |
        +-----------------------+
          |          |          |
     Worker 1    Worker 2    Worker 3
     MQ active   MQ replica  MQ replica
       PVC 1       PVC 2       PVC 3
```

Native HA n'est pas un partage de disque : chaque instance possède son stockage et reçoit les écritures du recovery log répliquées par l'instance active.

La cible Azure ajoute un objectif de placement des trois workers sur trois Availability Zones distinctes lorsque la région ARO choisie le permet.

## Manifeste

`deploy/native-ha/qm-prod.yaml` utilise l'API `mq.ibm.com/v1beta1` et prépare :

- IBM MQ `9.4.5.1-r1` comme version candidate à revalider au moment du lab Azure ;
- `availability.type: NativeHA` ;
- stockage persistant RWO de 10 Gi par instance, sans suppression automatique du claim ;
- TLS pour le trafic de réplication Native HA ;
- configuration MQSC mTLS/OAM issue de `qm-prod-security` ;
- métriques activées avec ServiceMonitor ;
- console web désactivée ;
- route MQ désactivée par défaut.

Le manifeste est une cible de déploiement, pas une preuve d'exécution.

### Garde-fou licence

Le dépôt conserve volontairement :

```yaml
license:
  accept: false
```

Ne jamais passer à `true` avant d'avoir validé entitlement, licence applicable, version Operator/MQ et coût de l'environnement. Le dépôt ne constitue pas une acceptation de licence.

## Secrets attendus — jamais dans Git

Namespace : `mayabank-mq-prod`.

- `qm-prod-replication-tls` : secret TLS de réplication Native HA ;
- `qm-prod-server-tls` : clé/certificat serveur channels applicatifs ;
- `qm-prod-client-ca` : CA clients ;
- certificats clients `payment-order` et `payment-processing` ;
- identité/certificat de monitoring dédié `mqmonitor` si requis.

Pour Azure, Azure Key Vault est la cible de coffre. Le mécanisme de synchronisation vers OpenShift sera figé à l'implémentation. Les clés privées restent hors Git.

## Pré-flight cluster

Avant déploiement :

```bash
oc get nodes -o wide
oc get nodes -L topology.kubernetes.io/zone,kubernetes.io/hostname
oc get sc
oc get csv -A | grep -i mq
oc api-resources | grep -i queuemanager
```

Critères :

1. au moins trois workers schedulables ;
2. workers distincts au niveau `kubernetes.io/hostname` ;
3. cible Azure : trois zones distinctes si disponibles ;
4. StorageClass RWO dynamique et testée ;
5. IBM MQ Operator compatible avec la version OpenShift/ARO ;
6. namespace, quotas et capacité CPU/RAM suffisants ;
7. entitlement/licence confirmés ;
8. coût du lab validé.

CRC mono-nœud ne satisfait pas ces critères et ne peut pas servir de preuve Native HA.

## Déploiement futur Azure

Après création des secrets et validation explicite de la licence :

```bash
oc apply -f deploy/security/qm-prod-security.yaml
oc apply -f deploy/native-ha/qm-prod.yaml

oc -n mayabank-mq-prod get qmgr qm-prod -o yaml
oc -n mayabank-mq-prod get pods,pvc -o wide
```

Vérifier que les trois pods MQ sont placés sur trois workers distincts et, pour le lab ARO multi-AZ, sur trois zones distinctes. Si ce n'est pas le cas, arrêter le test et traiter le placement via les mécanismes supportés par IBM MQ Operator ; ne jamais éditer directement le StatefulSet géré par l'Operator.

## Contrôle Native HA

Depuis le pod actif :

```bash
oc -n mayabank-mq-prod exec -it <pod-actif> -- dspmq -o nativeha -x -m QM.PROD
```

Attendu avant test :

- une instance `ROLE(Active)` ;
- deux instances `ROLE(Replica)` ;
- replicas `INSYNC(Yes)` ;
- majorité disponible ;
- backlog de réplication revenu à zéro en régime nominal.

Conserver cette sortie comme preuve datée.

## Test 1 — perte du pod actif

Le script fourni est volontairement protégé :

```bash
I_UNDERSTAND_FAILOVER_TEST=yes \
  bash scripts/native-ha/failover-pod.sh
```

Il :

1. exige trois pods ;
2. identifie l'active avec `dspmq -o nativeha -x` ;
3. supprime uniquement le pod actif ;
4. mesure le temps avant apparition d'une nouvelle active ;
5. attend le retour du groupe à trois pods Ready.

Avant utilisation, confirmer le label des pods. Si le sélecteur diffère :

```bash
MQ_POD_SELECTOR='<label>=<value>' \
I_UNDERSTAND_FAILOVER_TEST=yes \
  bash scripts/native-ha/failover-pod.sh
```

## Test 2 — perte d'un worker

À réaliser uniquement sur un cluster de lab dédié.

Procédure :

1. identifier le worker hébergeant l'instance active ;
2. lancer un flux de paiements continu ;
3. enregistrer T0 ;
4. simuler la perte du worker selon le mécanisme Azure/ARO retenu ;
5. mesurer reconnexion JMS, nouvelle active, erreurs applicatives, messages perdus/dupliqués ;
6. restaurer le worker ;
7. attendre le retour des trois instances `INSYNC(Yes)` ;
8. vérifier l'idempotence PostgreSQL.

Le client Java de ce dépôt est préparé pour la reconnexion automatique au même queue manager avec `WMQ_CLIENT_RECONNECT_Q_MGR`. Il supporte également une `MQ_CONNECTION_NAME_LIST` et `MQ_AUTH_MODE=mtls`.

## Test 3 — zone de disponibilité

Sur ARO multi-AZ, valider la perte de capacité d'une zone par un scénario de lab contrôlé lorsque cela est techniquement et économiquement acceptable.

Critères :

- majorité conservée ;
- nouvelle active opérationnelle ;
- client reconnecté ;
- messages persistants conservés ;
- groupe revenu à 3/3 après restauration.

## Test 4 — quorum

Le but est de comprendre le comportement, pas de provoquer une panne destructrice non maîtrisée.

- 3/3 : fonctionnement nominal ;
- perte d'une instance : majorité conservée ;
- perte de deux instances : plus de majorité, aucune promesse de traitement ;
- restaurer les instances et attendre la resynchronisation avant de poursuivre.

## Sécurité production

La cible de production n'utilise pas le principal CRC générique `app`.

Identités :

- `paymentorder` ;
- `paymentproc` ;
- `mqmonitor` ;
- identités d'administration séparées.

Le monitoring queue-level reçoit uniquement les autorisations nécessaires aux commandes/statuts MQ et à la lecture de métriques. Les applications ne reçoivent aucun droit de monitoring supplémentaire.

## Observabilité

La cible I4 réutilise les enseignements I3 :

```text
IBM MQ -> metrics/collector -> ServiceMonitor -> UWM Prometheus -> Grafana/Alertmanager
```

Dashboard minimum Azure :

- QM status ;
- état Native HA / replicas ;
- queue depth ;
- oldest message age ;
- DLQ ;
- BACKOUT ;
- CPU/RAM ;
- PVC/storage ;
- scrape target.

Les métriques réellement présentes doivent être inventoriées avant d'activer une règle ou un dashboard. Aucun nom de métrique ne doit être supposé.

## HA != PRA

Native HA protège principalement contre une panne de pod/nœud/zone au sein d'une région. Ce n'est pas à lui seul un PRA inter-région.

Le PRA doit avoir ses propres décisions : région secondaire, réplication, sauvegarde/restauration, RPO/RTO, dépendances applicatives et procédures de bascule. Native HA Cross-Region Replication peut être étudié séparément ; elle ne doit pas être revendiquée tant qu'elle n'est pas testée.

## Preuves à conserver

Créer `evidence/i4-azure-YYYYMMDD/` avec :

- commit Git exact ;
- versions ARO/OpenShift, MQ Operator et MQ ;
- région/zones Azure ;
- `oc get nodes -o wide` avec labels de zone ;
- `oc get pods,pvc -o wide` ;
- état `QueueManager` ;
- `dspmq -o nativeha -x` avant/après panne ;
- durée de failover ;
- logs client autour de la reconnexion ;
- résultats paiements avant/pendant/après panne ;
- contrôle PostgreSQL d'absence de double effet ;
- métriques/dashboards autour de T0 ;
- coût du lab ;
- preuve de destruction/nettoyage des ressources cloud.

## Critère de clôture I4

### Design

`DESIGN_COMPLETE` est atteint : architecture Native HA, sécurité, stockage, réseau, observabilité, GitOps, tests de résilience et plan Azure sont définis.

### Runtime

`RUNTIME_VALIDATED` sera atteint uniquement après exécution des tests pod + worker sur un cluster ARO réellement multi-worker, et conservation des preuves.

Formulation correcte avant le lab Azure :

> IBM MQ Native HA on OpenShift/ARO — complete target design and deployment/runbook assets; Azure multi-worker runtime validation pending.

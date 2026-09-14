# I4 — IBM MQ Native HA sur OpenShift multi-worker

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
        | Raft / quorum         |
        +-----------------------+
          |          |          |
     Worker 1    Worker 2    Worker 3
     MQ active   MQ replica  MQ replica
       PVC 1       PVC 2       PVC 3
```

Native HA n'est pas un partage de disque : chaque instance possède son stockage et reçoit les écritures du recovery log répliquées par l'instance active.

## Manifeste

`deploy/native-ha/qm-prod.yaml` utilise l'API `mq.ibm.com/v1beta1` et :

- IBM MQ `9.4.5.1-r1` ;
- `availability.type: NativeHA` ;
- stockage persistant RWO de 10 Gi par instance, sans suppression automatique du claim ;
- TLS pour le trafic de réplication Native HA ;
- configuration MQSC mTLS/OAM issue de `qm-prod-security` ;
- métriques activées avec ServiceMonitor et certificat OpenShift ;
- console web désactivée ;
- route MQ désactivée par défaut pour garder l'exposition interne pendant le lab.

### Garde-fou licence

Le dépôt conserve volontairement :

```yaml
license:
  accept: false
```

Ne jamais passer à `true` avant d'avoir validé entitlement, licence applicable, version Operator/MQ et coût de l'environnement. Le dépôt ne constitue pas une acceptation de licence.

## Secrets attendus — jamais dans Git

Namespace : `mayabank-mq-prod`.

- `qm-prod-replication-tls` : secret `kubernetes.io/tls` utilisé pour chiffrer la réplication Native HA ;
- `qm-prod-server-tls` : clé/certificat serveur pour les channels applicatifs ;
- `qm-prod-client-ca` : CA de confiance des certificats clients ;
- certificats clients `payment-order` et `payment-processing`, montés uniquement dans leurs workloads.

Le format et la rotation seront intégrés à Vault/External Secrets ou cert-manager sur la plateforme cible. Pour un lab, une PKI locale dédiée est acceptable si les clés privées restent hors Git.

## Pré-flight cluster

Avant déploiement :

```bash
oc get nodes -o wide
oc get sc
oc get csv -A | grep -i mq
oc api-resources | grep -i queuemanager
```

Critères :

1. au moins trois workers schedulables ;
2. workers distincts au niveau `kubernetes.io/hostname` ;
3. StorageClass RWO testée, dynamique et suffisamment performante ;
4. IBM MQ Operator compatible avec la version OpenShift ;
5. namespace, quotas et capacité CPU/RAM suffisants ;
6. entitlement/licence confirmés.

CRC mono-nœud ne satisfait pas ces critères et ne peut pas servir de preuve Native HA.

## Déploiement

Après création des secrets et validation explicite de la licence :

```bash
oc apply -f deploy/security/qm-prod-security.yaml
oc apply -f deploy/native-ha/qm-prod.yaml

oc -n mayabank-mq-prod get qmgr qm-prod -o yaml
oc -n mayabank-mq-prod get pods,pvc -o wide
```

Vérifier que les trois pods MQ sont placés sur trois workers distincts. Si ce n'est pas le cas, arrêter le test et traiter le placement via les mécanismes supportés par l'Operator ; ne pas éditer directement le StatefulSet géré par l'Operator.

## Contrôle Native HA

Depuis le pod actif :

```bash
oc -n mayabank-mq-prod exec -it <pod-actif> -- dspmq -o nativeha -x -m QM.PROD
```

Attendu avant test :

- une instance `ROLE(Active)` ;
- deux instances `ROLE(Replica)` ;
- replicas `INSYNC(Yes)` ;
- quorum `3/3` ;
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

À réaliser uniquement sur un cluster de lab dédié. Ne pas automatiser aveuglément un drain de nœud partagé.

Procédure recommandée :

1. identifier le worker hébergeant l'instance active ;
2. lancer en continu un flux de paiements avec identifiants uniques et timestamps ;
3. enregistrer l'heure T0 ;
4. simuler la perte du worker selon le mécanisme du lab ;
5. mesurer : temps de reconnexion JMS, temps jusqu'à nouvelle active, erreurs applicatives, messages perdus/dupliqués, backlog ;
6. restaurer le worker ;
7. attendre le retour des trois instances `INSYNC(Yes)` ;
8. vérifier l'idempotence métier dans PostgreSQL.

Le client Java de ce dépôt est préparé pour la reconnexion automatique au **même queue manager** avec `WMQ_CLIENT_RECONNECT_Q_MGR`. Il supporte également une `MQ_CONNECTION_NAME_LIST` et un mode `MQ_AUTH_MODE=mtls`.

## Test 3 — quorum

Le but est de comprendre le comportement, pas de provoquer une panne destructrice non maîtrisée.

- 3/3 : fonctionnement nominal ;
- perte d'une instance : majorité conservée, le service peut continuer ;
- perte de deux instances : plus de majorité, aucune promesse de traitement ne doit être faite ;
- restaurer les instances et attendre la resynchronisation avant de poursuivre.

## HA != PRA

Native HA protège principalement contre une panne de pod/nœud au sein d'un groupe. Ce n'est pas à lui seul un PRA inter-site.

Le PRA doit avoir ses propres décisions : site/région de secours, réplication, sauvegarde/restauration, RPO/RTO, dépendances applicatives et procédures de bascule. Une extension Native HA CRR peut être étudiée séparément ; elle ne doit pas être revendiquée tant qu'elle n'est pas testée.

## Preuves à conserver

Créer un dossier d'évidence daté contenant :

- commit Git exact ;
- versions OpenShift, MQ Operator et MQ ;
- `oc get nodes -o wide` ;
- `oc get pods,pvc -o wide` ;
- état `QueueManager` ;
- `dspmq -o nativeha -x` avant et après panne ;
- durée de failover ;
- logs client autour de la reconnexion ;
- résultat des paiements avant/pendant/après panne ;
- contrôle PostgreSQL d'absence de double effet ;
- métriques/dashboards autour de T0 ;
- coût du lab et preuve de destruction des ressources cloud le cas échéant.

## Critère de clôture I4

I4 devient `RUNTIME_VALIDATED` uniquement lorsque les tests pod + worker ont été exécutés sur un cluster réellement multi-worker et les preuves ci-dessus conservées.

Le dépôt fournit désormais la **cible et les sondes**, mais tant que ces sorties n'existent pas la formulation correcte reste : **Native HA designed/implemented, runtime validation pending**.

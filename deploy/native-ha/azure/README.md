# Azure ARO Native HA — implementation boundary

Statut : **DESIGN ONLY / DO NOT APPLY**.

Ce répertoire réserve la future implémentation Azure de l'I4. Aucun manifeste cloud exécutable n'est livré ici tant que budget, quotas, versions, entitlement IBM MQ et région Azure ne sont pas validés.

## Fichiers prévus lors de l'implémentation

```text
deploy/native-ha/azure/
├── README.md
├── kustomization.yaml                 # futur
├── namespace.yaml                     # futur
├── queuemanager-patch.yaml            # futur, uniquement champs vérifiés Operator
├── network-policies.yaml              # futur
├── servicemonitor.yaml                # futur si nécessaire
├── prometheus-rules.yaml              # futur
├── external-secrets/                  # futur, sans secret brut
└── overlays/
    └── lab/                            # futur
```

L'infrastructure Azure elle-même (ARO, VNet, subnets, Key Vault, budgets, DNS, MachineSets/capacité) sera créée par une couche IaC séparée. Ce dépôt conserve la configuration workload/GitOps MQ.

## Gates avant ajout de manifests Azure

- version ARO/OpenShift figée ;
- version IBM MQ Operator compatible ;
- version QueueManager/MQ compatible ;
- région et Availability Zones validées ;
- StorageClass Azure Disk RWO inspectée ;
- labels/taints/placement supportés par l'Operator confirmés ;
- mécanisme Key Vault -> OpenShift Secret validé ;
- entitlement/licence confirmé ;
- budget Azure accepté.

## Interdictions

- ne jamais committer de clé privée ;
- ne jamais committer de pull secret ;
- ne jamais committer de kubeconfig ;
- ne jamais committer de mot de passe ;
- ne jamais committer d'état Terraform ;
- ne jamais positionner `license.accept: true` par défaut ;
- ne jamais patcher directement le StatefulSet généré par IBM MQ Operator ;
- ne jamais présenter un fichier de ce répertoire comme preuve runtime.

Voir :

- `docs/18-i4-native-ha.md`
- `docs/19-i4-azure-aro-target-design.md`
- `docs/20-i4-azure-implementation-plan.md`

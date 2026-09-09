# FinOps et accès GCP

## État

Aucune ressource créée. Aucun accès GCP configuré. Aucun terraform apply autorisé par ce lot.
Le choix GCP répond à l'offre de mission ; ce n'est pas une affirmation que ce cloud est le moins cher.

## Hypothèse, pas devis

Cadrage initial : OpenShift autogéré, trois contrôles + trois workers, disques système/MQ, réseau, faible trafic.
L'enveloppe de discussion de 1,50–2,50 USD HT/h et 150 USD HT pour une première campagne courte est provisoire et n'est pas un plafond garanti.
Les tarifs exacts régionaux et le dimensionnement n'ont pas été validés dans un devis SKU. Ne pas déclencher un déploiement sur cette seule estimation.

## Chiffrage obligatoire avant création

- Région/zones, familles et tailles de VM, durée bootstrap et cluster.
- Disques système et données, classe, capacité, snapshots et rétention.
- Load balancers, IP, NAT si nécessaire, DNS.
- Trafic inter-zone et sortant, logs, métriques, registre et stockage des preuves.
- Licences/évaluation OpenShift et MQ, éligibilité effective.
- Taxes, devise du compte, marge et crédits éventuels présentés séparément.
- Coût des ressources conservées entre deux sessions.

Terraform plan décrit des changements, pas un prix.

## Contrôles

Projet dédié, labels projet/propriétaire/environnement/expiration.
Alertes budgétaires avec seuils ; ne pas les présenter comme coupe-circuit garanti.
Pas d'engagement long terme pour ce laboratoire.
Fenêtre de test approuvée, export des preuves avant nettoyage.
Planifier le nettoyage selon les propriétaires Terraform/installateur ; ne pas appliquer un destroy aveugle.
Lister après suppression les VM, disques, IP, LB, snapshots et autres services résiduels.
Un arrêt des VM ne supprime pas la facturation de tous ces postes.

## Accès

L'utilisateur conserve son authentification GCP.
Préférer droits limités au projet et authentification sans clé longue durée lorsque le parcours choisi le permet.
Pas de partage de mot de passe, token, clé JSON ou pull secret dans la conversation ou Git.
Le premier contrôle sera en lecture seule.

## Références officielles

- https://cloud.google.com/products/compute/pricing/general-purpose
- https://cloud.google.com/compute/disks-image-pricing
- https://cloud.google.com/vpc/network-pricing
- https://docs.cloud.google.com/billing/docs/how-to/budgets
- https://www.redhat.com/en/technologies/cloud-computing/openshift/try-it
- https://www.ibm.com/docs/en/ibm-mq/9.4.x?topic=mq-license-information

# Prérequis CRC — collecte non destructive

## But

Qualifier le poste réel avant de choisir les versions et d'allouer des ressources. Ne pas supposer que l'installation précédemment décrite est encore disponible ou démarrée.

Dans le terminal déjà utilisé pour CRC, exécuter uniquement :

~~~bash
crc version
crc status
crc config view
oc version --client
~~~

Si le cluster est démarré et que la session oc est déjà authentifiée :

~~~bash
oc whoami
oc get nodes -o wide
oc get clusterversion
oc get clusteroperators
oc get storageclass
oc get pvc -A
oc adm top nodes
~~~

Une commande non autorisée ou une métrique absente est à noter, pas à contourner.
Ne pas exécuter de commande de connexion avec un token dans un compte rendu partagé.

## Informations à relever

- RAM physique totale et RAM libre du PC ; CPU disponibles.
- RAM/vCPU/disque alloués à CRC ; espace disque libre.
- Version réelle d'OpenShift et état des opérateurs.
- StorageClass par défaut, provisionnement dynamique et politiques de rétention.
- Droits pour installer les opérateurs ; accès réseau aux registres nécessaires.
- Disponibilité Maven/Java, moteur de conteneurs et Git, à contrôler au lot applicatif.

Aucun dimensionnement complet n'est validé à ce stade. MQ, Tekton, Argo CD, Vault et la supervision ne seront pas tous installés simultanément sans contrôle de capacité.

## Compatibilité et licences

Avant déploiement : consigner versions exactes et matrice de compatibilité officielle IBM/Red Hat.
Confirmer l'accès aux images MQ Developer et la méthode supportée pour le laboratoire retenu.
Ne pas confondre disponibilité d'une fonctionnalité dans une édition développeur et accès automatique à toutes les images/opérateurs.
L'évaluation OpenShift cloud et les droits IBM doivent être vérifiés séparément.

## Ne pas faire dans ce lot

Pas de crc delete/cleanup, pas de modification des ressources, pas de suppression de PVC.
Pas d'installation cloud, pas de téléchargement de clés dans Git.
Les fichiers contenant token, certificat privé, kubeconfig ou pull secret restent hors dépôt.

## Sortie attendue

Un compte rendu expurgé : versions, capacité, stockage, droits, anomalies et décision GO/NO-GO.
La suite commence seulement après cette qualification.

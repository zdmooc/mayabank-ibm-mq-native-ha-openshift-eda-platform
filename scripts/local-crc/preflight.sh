#!/usr/bin/env bash
set -euo pipefail
command -v oc >/dev/null
oc whoami
nodes="$(oc get nodes -o name)"
if [[ "$nodes" != "node/crc" ]]; then
  echo "STOP : attendu un seul nœud nommé crc, reçu : $nodes" >&2
  exit 1
fi
version="$(oc get clusterversion version -o jsonpath='{.status.desired.version}')"
[[ "$version" == "4.22.7" ]] || { echo "STOP : version $version à requalifier."; exit 1; }
oc wait --for=condition=Ready node/crc --timeout=30s
oc get clusteroperators
oc get storageclass crc-csi-hostpath-provisioner
oc get pvc -A
oc adm top nodes
oc describe node crc | sed -n '/Allocated resources:/,/Events:/p'
echo "Précontrôle terminé. Vérifier aussi l'espace disque via crc status."

#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
bash "$root/scripts/local-crc/preflight.sh"
echo "Ce script crée MQ uniquement dans mayabank-mq-local ; il accepte la licence IBM Developer."
echo "Réservations proposées : 500m CPU, 1Gi RAM ; limite 2Gi RAM ; PVC 5Gi."
read -r -p "Après lecture de la licence et contrôle des ressources, taper DEPLOY-LOCAL : " answer
[[ "$answer" == "DEPLOY-LOCAL" ]] || exit 1
oc apply -f "$root/deploy/local-crc/mq.yaml"
oc -n mayabank-mq-local rollout status deployment/mq --timeout=600s
oc -n mayabank-mq-local get pods,pvc
echo "Déployé. Lancer ensuite bash scripts/local-crc/verify.sh"

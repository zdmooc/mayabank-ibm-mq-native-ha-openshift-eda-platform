#!/usr/bin/env bash
set -euo pipefail
# oc.exe doit convertir les chemins des fichiers locaux sous Git Bash.
case "${OSTYPE:-}" in
  msys*|cygwin*) unset MSYS_NO_PATHCONV MSYS2_ARG_CONV_EXCL ;;
esac
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
bash "$root/scripts/local-crc/preflight.sh"
oc -n mayabank-mq-local get deployment mq >/dev/null
command -v openssl >/dev/null
echo "Lot paiement : build Java, redémarrage MQ, connexion app authentifiée en TCP interne."
echo "Pas de TLS dans ce lot local. Aucun accès externe. Wero reste inchangé."
read -r -p "Taper DEPLOY-PAYMENTS : " answer
[[ "$answer" == "DEPLOY-PAYMENTS" ]] || exit 1

# Build avant de modifier MQ. Le namespace de build peut joindre Maven et les registres.
oc apply -f "$root/deploy/payments/build.yaml"
oc -n mayabank-mq-build start-build payments --from-dir="$root/apps/payments" --follow --wait
image="$(oc -n mayabank-mq-build get istag payments:0.2.0 -o jsonpath='{.image.dockerImageReference}')"
[[ "$image" == *@sha256:* ]] || { echo "STOP : digest image absent."; exit 1; }

# Secret généré localement, jamais affiché ni passé en argument.
if [[ -z "$(oc -n mayabank-mq-local get secret mq-app-credentials --ignore-not-found -o name)" ]]; then
  credential_file="$(mktemp)"
  chmod 600 "$credential_file"
  trap 'rm -f -- "$credential_file"' EXIT
  openssl rand -hex 24 | tr -d '\r\n' > "$credential_file"
  oc -n mayabank-mq-local create secret generic mq-app-credentials --from-file="mqAppPassword=$credential_file"
  rm -f -- "$credential_file"
  trap - EXIT
fi
oc apply -f "$root/deploy/payments/mq-connected.yaml"
# Le montage ConfigMap subPath nécessite un nouveau Pod pour relire MQSC.
oc -n mayabank-mq-local rollout restart deployment/mq
oc -n mayabank-mq-local rollout status deployment/mq --timeout=600s
bash "$root/scripts/payments/ensure-htp-order.sh"
oc apply -f "$root/deploy/payments/network.yaml"
oc -n mayabank-mq-build policy add-role-to-user system:image-puller system:serviceaccount:mayabank-mq-local:payments
oc set image --local -f "$root/deploy/payments/processor.yaml" "payments=$image" -o yaml |
  oc apply -f -
oc -n mayabank-mq-local rollout status deployment/payment-processing --timeout=180s
echo "Conteneur déployé ; la connexion JMS sera vérifiée par les Jobs."
bash "$root/scripts/payments/verify.sh"

#!/usr/bin/env bash
set -euo pipefail
# Conversion des chemins locaux pour oc.exe, limitée à ce script.
case "${OSTYPE:-}" in
  msys*|cygwin*) unset MSYS_NO_PATHCONV MSYS2_ARG_CONV_EXCL ;;
esac
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
[[ "$(oc get nodes -o name)" == "node/crc" ]] || { echo "STOP : cluster inattendu."; exit 1; }
image="$(oc -n mayabank-mq-local get deployment payment-processing -o jsonpath='{.spec.template.spec.containers[0].image}')"
[[ "$image" == *@sha256:* ]] || { echo "STOP : image non épinglée."; exit 1; }
started="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
for test_name in backout order; do
  job="$(oc set image --local -f "$root/deploy/payments/$test_name-job.yaml" "payments=$image" -o yaml |
    oc create -f - -o name)"
  # Détecter aussi un Pod échoué : la condition Failed du Job peut arriver plus tard.
  deadline=$((SECONDS + 150))
  completed=false
  while (( SECONDS < deadline )); do
    state="$(oc -n mayabank-mq-local get "$job" -o jsonpath='{.status.succeeded}{"|"}{.status.failed}')"
    if [[ "$state" == 1\|* ]]; then
      completed=true
      break
    fi
    phases="$(oc -n mayabank-mq-local get pods -l "batch.kubernetes.io/job-name=${job#*/}" -o jsonpath='{range .items[*]}{.status.phase}{" "}{end}')"
    if [[ "${state#*|}" =~ ^[1-9] ]] || [[ "$phases" == *Failed* ]]; then
      echo "FAIL : $job a échoué."
      break
    fi
    sleep 2
  done
  if [[ "$completed" != true ]]; then
    echo "Vérification interrompue : $job n'a pas réussi."
    oc -n mayabank-mq-local logs "$job" || true
    oc -n mayabank-mq-local logs deployment/payment-processing --tail=30 || true
    oc -n mayabank-mq-local get pods
    exit 1
  fi
  output="$(oc -n mayabank-mq-local logs "$job")"
  printf '%s\n' "$output"
  printf '%s\n' "$output" | grep -Fq 'PASS:' || { echo "FAIL : verdict absent."; exit 1; }
  if [[ "$test_name" == backout ]]; then
    correlation="$(printf '%s\n' "$output" | sed -n 's/^INVALID_SENT correlationId=//p')"
    [[ "$correlation" =~ ^[0-9a-f-]{36}$ ]] || { echo "FAIL : identifiant absent."; exit 1; }
    processor_logs="$(oc -n mayabank-mq-local logs deployment/payment-processing --since-time="$started")"
    for expected in "RETRY correlationId=$correlation deliveryCount=1" "RETRY correlationId=$correlation deliveryCount=2" "BACKOUT correlationId=$correlation deliveryCount=3"; do
      grep -Fxq "$expected" <<< "$processor_logs" || { echo "FAIL : trace attendue absente : $expected"; exit 1; }
      printf '%s\n' "$expected"
    done
  fi
done
oc -n mayabank-mq-local logs deployment/payment-processing --since-time="$started"
echo "PASS lot backout : message invalide isolé puis paiement valide."

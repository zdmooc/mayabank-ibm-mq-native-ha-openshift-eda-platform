#!/usr/bin/env bash
set -euo pipefail
case "${OSTYPE:-}" in msys*|cygwin*) unset MSYS_NO_PATHCONV MSYS2_ARG_CONV_EXCL ;; esac
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ns=mayabank-mq-local
[[ "$(oc get nodes -o name)" == node/crc ]] || { echo 'STOP : CRC uniquement.'; exit 1; }
oc -n "$ns" rollout status deployment/payments-db --timeout=180s
oc -n "$ns" rollout status deployment/payment-processing --timeout=180s
image="$(oc -n "$ns" get deployment payment-processing -o jsonpath='{.spec.template.spec.containers[0].image}')"
[[ "$image" == *@sha256:* ]] || { echo 'STOP : image non épinglée.'; exit 1; }

new_id() {
  local raw
  raw="$(openssl rand -hex 16)"
  printf '%s-%s-%s-%s-%s' "${raw:0:8}" "${raw:8:4}" "${raw:12:4}" "${raw:16:4}" "${raw:20:12}"
}
run_test() {
  local scenario="$1" id="$2" job deadline state phases output
  job="$(oc set image --local -f "$root/deploy/idempotency/test-job.yaml" "payments=$image" -o yaml |
    oc set env --local -f - "IDEMPOTENCY_SCENARIO=$scenario" "IDEMPOTENCY_PAYMENT_ID=$id" -o yaml |
    oc create -f - -o name)"
  deadline=$((SECONDS + 330))
  while (( SECONDS < deadline )); do
    state="$(oc -n "$ns" get "$job" -o jsonpath='{.status.succeeded}{"|"}{.status.failed}')"
    [[ "$state" == 1\|* ]] && break
    phases="$(oc -n "$ns" get pods -l "batch.kubernetes.io/job-name=${job#*/}" -o jsonpath='{range .items[*]}{.status.phase}{" "}{end}')"
    if [[ "${state#*|}" =~ ^[1-9] ]] || [[ "$phases" == *Failed* ]]; then break; fi
    sleep 2
  done
  output="$(oc -n "$ns" logs "$job")"
  printf '%s\n' "$output"
  if [[ "$state" != 1\|* ]] || ! grep -Fq "PASS: idempotency scenario=$scenario" <<< "$output"; then
    oc -n "$ns" logs deployment/payment-processing --tail=30 || true
    return 1
  fi
}

# Test IDs are fresh; no purge of queues or database is needed.
id="$(new_id)"
run_test baseline "$id"
oc -n "$ns" rollout restart deployment/payment-processing
oc -n "$ns" rollout status deployment/payment-processing --timeout=180s
run_test after-restart "$id"

cleanup() {
  local result=$?
  trap - EXIT
  if ! oc -n "$ns" set env deployment/payment-processing MAYABANK_ENABLE_CRASH_PROBE-; then
    echo 'ATTENTION : retirer MAYABANK_ENABLE_CRASH_PROBE manuellement.'
    exit 1
  fi
  oc -n "$ns" rollout status deployment/payment-processing --timeout=180s || result=1
  if [[ "$result" == 0 ]]; then
    bash "$root/scripts/payments/verify.sh" || result=$?
  fi
  if [[ "$result" == 0 ]]; then
    echo 'PASS lot idempotence : doublon, conflit, redémarrage, crash DB/MQ et effet unique ; injection désactivée.'
  fi
  exit "$result"
}
echo 'Test contrôlé : arrêt du processeur après commit DB, puis redémarrage automatique.'
trap cleanup EXIT
oc -n "$ns" set env deployment/payment-processing MAYABANK_ENABLE_CRASH_PROBE=true
oc -n "$ns" rollout status deployment/payment-processing --timeout=180s
crash_id="$(new_id)"
run_test crash "$crash_id"
previous="$(oc -n "$ns" logs deployment/payment-processing --previous)"
grep -Fx "CRASH_AFTER_DB_COMMIT paymentId=$crash_id" <<< "$previous"
exit_code="$(oc -n "$ns" get pods -l app=payment-processing -o jsonpath='{.items[0].status.containerStatuses[0].lastState.terminated.exitCode}')"
[[ "$exit_code" == 75 ]] || { echo 'FAIL : arrêt injecté non confirmé.'; exit 1; }
echo 'Preuves du crash vérifiées ; désactivation de l’injection et contrôle JMS final.'

#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
[[ "$(oc get nodes -o name)" == "node/crc" ]] || { echo "STOP : cluster inattendu."; exit 1; }
image="$(oc -n mayabank-mq-local get deployment payment-processing -o jsonpath='{.spec.template.spec.containers[0].image}')"
[[ "$image" == *@sha256:* ]] || { echo "STOP : image non épinglée."; exit 1; }
for test_name in auth-negative order; do
  job="$(oc set image --local -f "$root/deploy/payments/$test_name-job.yaml" "payments=$image" -o yaml |
    oc create -f - -o name)"
  # Un échec est imprimé avec ses logs ; rien n'est déclaré PASS sur le seul état Running.
  if ! oc -n mayabank-mq-local wait --for=condition=complete "$job" --timeout=150s; then
    oc -n mayabank-mq-local logs "$job" || true
    oc -n mayabank-mq-local get pods
    exit 1
  fi
  output="$(oc -n mayabank-mq-local logs "$job")"
  printf '%s\n' "$output"
  printf '%s\n' "$output" | grep -Fq 'PASS:' || { echo "FAIL : verdict absent."; exit 1; }
done
oc -n mayabank-mq-local logs deployment/payment-processing --tail=15
echo "PASS lot paiement : refus mauvais mot de passe puis demande/réponse JMS."

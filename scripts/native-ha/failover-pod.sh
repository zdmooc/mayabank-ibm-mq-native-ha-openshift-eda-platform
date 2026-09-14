#!/usr/bin/env bash
set -euo pipefail

ns="${MQ_NAMESPACE:-mayabank-mq-prod}"
qmgr="${MQ_QMGR_NAME:-QM.PROD}"
selector="${MQ_POD_SELECTOR:-app.kubernetes.io/instance=qm-prod}"

[[ "${I_UNDERSTAND_FAILOVER_TEST:-no}" == "yes" ]] || {
  echo "STOP: this script deletes the active MQ pod. Run only on a dedicated lab cluster with I_UNDERSTAND_FAILOVER_TEST=yes."
  exit 2
}

mapfile -t pods < <(oc -n "$ns" get pods -l "$selector" -o name)
if (( ${#pods[@]} != 3 )); then
  echo "STOP: expected 3 Native HA pods with selector '$selector', found ${#pods[@]}."
  oc -n "$ns" get pods -o wide
  exit 1
fi

nativeha_status() {
  local pod="$1"
  oc -n "$ns" exec "$pod" -- dspmq -o nativeha -x -m "$qmgr" 2>/dev/null || true
}

active=""
for pod in "${pods[@]}"; do
  status="$(nativeha_status "$pod")"
  if grep -q 'ROLE(Active)' <<<"$status"; then
    active="$pod"
    printf '%s\n' "$status"
    break
  fi
done

[[ -n "$active" ]] || { echo "STOP: no active Native HA instance found."; exit 1; }

echo "Active before failure: $active"
start_epoch="$(date +%s)"
oc -n "$ns" delete "$active" --wait=false

new_active=""
deadline=$((SECONDS + 300))
while (( SECONDS < deadline )); do
  mapfile -t current < <(oc -n "$ns" get pods -l "$selector" -o name 2>/dev/null || true)
  for pod in "${current[@]}"; do
    [[ "$pod" == "$active" ]] && continue
    status="$(nativeha_status "$pod")"
    if grep -q 'ROLE(Active)' <<<"$status"; then
      new_active="$pod"
      printf '%s\n' "$status"
      break 2
    fi
  done
  sleep 2
done

[[ -n "$new_active" ]] || { echo "FAIL: no new active instance within 300s."; exit 1; }
end_epoch="$(date +%s)"
echo "PASS: failover active $active -> $new_active in $((end_epoch-start_epoch)) seconds."

echo "Waiting for the Native HA set to return to three pods..."
deadline=$((SECONDS + 600))
while (( SECONDS < deadline )); do
  count="$(oc -n "$ns" get pods -l "$selector" --no-headers 2>/dev/null | wc -l | tr -d ' ')"
  ready="$(oc -n "$ns" get pods -l "$selector" --no-headers 2>/dev/null | awk '$2 ~ /^1\/1$/ && $3=="Running" {c++} END{print c+0}')"
  if [[ "$count" == "3" && "$ready" == "3" ]]; then
    echo "PASS: three MQ pods are Running/Ready again."
    exit 0
  fi
  sleep 5
done

echo "WARN: failover succeeded but the set did not return to 3/3 Ready within 600s."
exit 3

#!/usr/bin/env bash
set -euo pipefail
# Git Bash doit laisser les chemins Linux de la commande distante inchangés.
export MSYS_NO_PATHCONV=1
[[ "$(oc get nodes -o name)" == "node/crc" ]] || { echo "STOP : cluster inattendu."; exit 1; }
oc -n mayabank-mq-local rollout status deployment/mq --timeout=120s
oc -n mayabank-mq-local exec deployment/mq -- dspmqver
oc -n mayabank-mq-local exec deployment/mq -- dspmq
printf '%s\n' 'DISPLAY QLOCAL(PAYMENT.*) CURDEPTH DEFPSIST BOQNAME BOTHRESH' |
  oc -n mayabank-mq-local exec -i deployment/mq -- runmqsc QM.MAYABANK
marker="MAYABANK-SMOKE-$(date -u +%Y%m%dT%H%M%SZ)-$RANDOM"
printf '%s\n\n' "$marker" |
  oc -n mayabank-mq-local exec -i deployment/mq -- /opt/mqm/samp/bin/amqsput LAB.SMOKE.Q QM.MAYABANK
result="$(oc -n mayabank-mq-local exec deployment/mq -- /opt/mqm/samp/bin/amqsget LAB.SMOKE.Q QM.MAYABANK)"
printf '%s\n' "$result"
printf '%s\n' "$result" | grep -Fq "$marker" || { echo "FAIL : message absent."; exit 1; }
oc -n mayabank-mq-local get pods -l app=mayabank-mq -o jsonpath='{range .items[*]}{.status.containerStatuses[0].imageID}{"\n"}{end}'
echo "PASS : publication/lecture locale via bindings. Ni JMS réseau, ni Native HA validés."

#!/usr/bin/env bash
# Exécuter avec bash : les options restent dans ce processus.
set -euo pipefail
[[ "$(oc get nodes -o name)" == "node/crc" ]] || { echo "STOP : cluster inattendu."; exit 1; }
ns=mayabank-mq-local
expected='icr.io/ibm-messaging/mq@sha256:28cd7e9dc413eced83b21e02cd3683966f19ef22867bbc7ca8c1ed19d062f986'
actual="$(oc -n "$ns" get deployment mq -o jsonpath='{.spec.template.spec.containers[0].image}')"
[[ "$actual" == "$expected" ]] || { echo "STOP : image MQ non prévue par ce correctif local."; exit 1; }
oc -n "$ns" rollout status deployment/mq --timeout=600s
result="$(MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*' oc -n "$ns" exec -i deployment/mq -- sh -s <<'SH'
set -eu
dir='/mnt/mqm/data/qmgrs/QM!MAYABANK'
base="$dir/autocfg/base_qm.ini"
test -f "$base"
test -r /opt/mqm/lib64/mqsimpleauth.so

# Accepter seulement les deux formes connues, sans modifier de stanza inconnue.
classify() {
  awk '
  function finish() {
    if (!active) return
    if (service != "AuthorizationService" || size != "0" || unknown) bad=1
    if (name == "MQSeries.UNIX.auth.service" && module == "amqzfu") order=order "O"
    else if (name == "Dev.HtpAuth.Service" && module == "/opt/mqm/lib64/mqsimpleauth.so") order=order "H"
    else bad=1
  }
  /^[[:space:]]*[A-Za-z][A-Za-z0-9_]*:[[:space:]]*$/ {
    finish()
    active=($0 ~ /^[[:space:]]*ServiceComponent:/)
    service=name=module=size=""; unknown=0
    next
  }
  active {
    line=$0
    sub(/^[[:space:]]*/, "", line); sub(/[[:space:]]*$/, "", line)
    if (line == "" || line ~ /^[*#;]/) next
    if (line ~ /^Service[[:space:]]*=/) {sub(/^[^=]*=[[:space:]]*/, "", line); service=line}
    else if (line ~ /^Name[[:space:]]*=/) {sub(/^[^=]*=[[:space:]]*/, "", line); name=line}
    else if (line ~ /^Module[[:space:]]*=/) {sub(/^[^=]*=[[:space:]]*/, "", line); module=line}
    else if (line ~ /^ComponentDataSize[[:space:]]*=/) {sub(/^[^=]*=[[:space:]]*/, "", line); size=line}
    else unknown=1
  }
  END {finish(); if(bad || (order != "O" && order != "HO")) exit 1; print order}
  ' "$1"
}
base_order="$(classify "$base")" || { echo "STOP : base HTP/OAM inattendue."; exit 1; }
if [ "$base_order" = O ]; then
  backup="$(mktemp "$base.backup-XXXXXX")"
  cp -p "$base" "$backup"
  tmp="$(mktemp "$base.tmp-XXXXXX")"
  trap 'rm -f "$tmp"' EXIT
  cp -p "$base" "$tmp"
  awk '
  /^[[:space:]]*ServiceComponent:[[:space:]]*$/ && !done {
    print "ServiceComponent:"
    print "   Service=AuthorizationService"
    print "   Name=Dev.HtpAuth.Service"
    print "   Module=/opt/mqm/lib64/mqsimpleauth.so"
    print "   ComponentDataSize=0"
    print ""
    done=1
  }
  {print}
  END {if (!done) exit 1}
  ' "$base" > "$tmp"
  test "$(classify "$tmp")" = HO
  mv "$tmp" "$base"
  echo "Base corrigée. Sauvegarde : $backup"
  echo RESTART_REQUIRED
else
  echo "Base HTP/OAM déjà correcte."
  effective="$(classify "$dir/qm.ini" 2>/dev/null || true)"
  if [ "$effective" != HO ]; then echo RESTART_REQUIRED; fi
fi
SH
)"
printf '%s\n' "$result"
if [[ "$result" == *RESTART_REQUIRED* ]]; then
  oc -n "$ns" rollout restart deployment/mq
  oc -n "$ns" rollout status deployment/mq --timeout=600s
fi
MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*' oc -n "$ns" exec deployment/mq -- sh -c '
first=$(awk "/^[[:space:]]*Name=/ {if (\$0 ~ /Dev.HtpAuth.Service|MQSeries.UNIX.auth.service/) {print; exit}}" "/mnt/mqm/data/qmgrs/QM!MAYABANK/qm.ini")
case "$first" in
  *Dev.HtpAuth.Service*) echo "PASS : HTP précède OAM dans qm.ini." ;;
  *) echo "STOP : ordre effectif incorrect après contrôle."; exit 1 ;;
esac
'
echo "Le test JMS reste nécessaire : bash scripts/payments/verify.sh"

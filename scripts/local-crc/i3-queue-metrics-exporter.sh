#!/usr/bin/env bash
set -euo pipefail

NS="${NS:-mayabank-mq-local}"
BUILD_NS="${BUILD_NS:-mayabank-mq-build}"
MANIFEST="deploy/observability/mq-prometheus-exporter-local.yaml"
EVIDENCE_DIR="${EVIDENCE_DIR:-evidence/i3-$(date +%Y%m%d)}"
PORT="${PORT:-19157}"

mkdir -p "$EVIDENCE_DIR"

echo "===== I3 QUEUE METRICS EXPORTER / APPLY ====="
oc get namespace "$NS" >/dev/null
oc get namespace "$BUILD_NS" >/dev/null
oc apply -f "$MANIFEST"

echo
echo "===== BUILD IBM MQ PROMETHEUS EXPORTER v5.7.1 ====="
BUILD="$(oc -n "$BUILD_NS" start-build mq-prometheus-exporter -o name)"
echo "BUILD=$BUILD"
# Keep the build log as evidence but never print Kubernetes Secret contents.
oc -n "$BUILD_NS" logs -f "$BUILD" 2>&1 | tee "$EVIDENCE_DIR/mq-exporter-build.txt"

if ! oc -n "$BUILD_NS" wait --for=condition=Complete "$BUILD" --timeout=15m; then
  echo "Exporter build did not complete successfully" >&2
  oc -n "$BUILD_NS" get "$BUILD" -o wide >&2 || true
  exit 1
fi

echo
echo "===== DEPLOY EXPORTER ====="
oc -n "$NS" rollout restart deployment/mq-prometheus-exporter
if ! oc -n "$NS" rollout status deployment/mq-prometheus-exporter --timeout=5m; then
  echo "Exporter deployment did not become Ready" >&2
  oc -n "$NS" get pods -l app=mayabank-mq-exporter -o wide >&2 || true
  oc -n "$NS" logs deployment/mq-prometheus-exporter --tail=200 >&2 || true
  exit 2
fi

oc -n "$NS" get pod -l app=mayabank-mq-exporter -o wide \
  | tee "$EVIDENCE_DIR/mq-exporter-pod.txt"
oc -n "$NS" logs deployment/mq-prometheus-exporter --tail=200 \
  | tee "$EVIDENCE_DIR/mq-exporter-runtime.txt"

echo
echo "===== READ EXPORTER /metrics ====="
PF_LOG="$(mktemp)"
oc -n "$NS" port-forward service/mq-prometheus-exporter "$PORT":9157 >"$PF_LOG" 2>&1 &
PF_PID=$!
cleanup() {
  kill "$PF_PID" >/dev/null 2>&1 || true
  rm -f "$PF_LOG"
}
trap cleanup EXIT

READY=0
for _ in $(seq 1 30); do
  if curl -fsS "http://127.0.0.1:${PORT}/metrics" > "$EVIDENCE_DIR/mq-queue-metrics-raw.txt"; then
    READY=1
    break
  fi
  sleep 2
done

if [ "$READY" -ne 1 ]; then
  echo "Unable to read exporter metrics" >&2
  cat "$PF_LOG" >&2 || true
  exit 3
fi

grep '^ibmmq_queue_' "$EVIDENCE_DIR/mq-queue-metrics-raw.txt" \
  > "$EVIDENCE_DIR/mq-queue-metrics.txt" || true

QUEUE_COUNT="$(grep -c '^ibmmq_queue_' "$EVIDENCE_DIR/mq-queue-metrics-raw.txt" || true)"
DEPTH_COUNT="$(grep -c '^ibmmq_queue_depth' "$EVIDENCE_DIR/mq-queue-metrics-raw.txt" || true)"

printf 'ibmmq_queue_* samples=%s\nibmmq_queue_depth samples=%s\n' \
  "$QUEUE_COUNT" "$DEPTH_COUNT" \
  | tee "$EVIDENCE_DIR/mq-queue-metrics-count.txt"

echo
echo "===== SAMPLE QUEUE METRICS ====="
grep -E '^ibmmq_queue_(depth|oldest|attribute_max_depth|mqput|mqget)' \
  "$EVIDENCE_DIR/mq-queue-metrics-raw.txt" \
  | head -80 || true

echo
echo "===== SERVICE MONITOR ====="
oc -n "$NS" get servicemonitor mayabank-mq-queue-metrics -o yaml \
  | tee "$EVIDENCE_DIR/mq-queue-servicemonitor.yaml" >/dev/null

if [ "$QUEUE_COUNT" -eq 0 ]; then
  echo >&2
  echo "Exporter is running but no ibmmq_queue_* metrics are exposed." >&2
  echo "Inspect mq-exporter-runtime.txt for MQRC/OAM errors before changing authorities." >&2
  exit 4
fi

echo
echo "PASS: queue-level IBM MQ metrics are exposed by mq_prometheus."

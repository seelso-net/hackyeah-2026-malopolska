#!/usr/bin/env bash
# Waits until the app has started and finished its startup AI work (vectors and suggestions).
#   scripts/wait-ready.sh                  # http://localhost:8080, up to 180 s
#   BASE=https://my-app.fly.dev TIMEOUT=300 scripts/wait-ready.sh
set -euo pipefail
BASE="${BASE:-http://localhost:8080}"
TIMEOUT="${TIMEOUT:-180}"
printf 'Waiting for %s ' "$BASE"
for _ in $(seq 1 "$TIMEOUT"); do
  if curl -sf "$BASE/q/health/started" >/dev/null 2>&1; then
    echo " ready"
    exit 0
  fi
  printf '.'
  sleep 1
done
echo " not ready after ${TIMEOUT}s"
curl -s "$BASE/q/health" || true
exit 1

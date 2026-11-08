#!/usr/bin/env bash
# Starts GLiNER services natively, ONE PROCESS PER CHECKPOINT so memory/CPU can be attributed per model.
# Usage: start-gliner.sh [minimal|all|<checkpoint> <port>]
#   minimal: fastino/gliner2.5-base-v1 on :8090
#   all:     base :8090, multi :8091, GLiNER2.5-Decide :8092, small :8093
# Env: GLINER_DEVICE=cpu|mps|auto (default cpu), GLINER_THREADS
source "$(dirname "$0")/lib.sh"
cd "$ROOT/inference/gliner"
[[ -x .venv/bin/uvicorn ]] || fail "Run scripts/setup-python.sh first"
start_one() {
  local ckpt="$1" port="$2"
  if curl -sf "http://localhost:$port/health" >/dev/null; then info "port $port already serving"; return; fi
  info "starting $ckpt on :$port (device ${GLINER_DEVICE:-cpu})"
  GLINER_MODELS="$ckpt" nohup .venv/bin/uvicorn app:app --host 127.0.0.1 --port "$port" > "$LOG_DIR/gliner-$port.log" 2>&1 &
  for _ in $(seq 1 300); do curl -sf "http://localhost:$port/health" >/dev/null && break; sleep 2; done
  curl -sf "http://localhost:$port/health" | python3 -c 'import json,sys;d=json.load(sys.stdin);print("  ",d["status"],list(d["models"]),d["errors"] or "")'
}
case "${1:-minimal}" in
  minimal) start_one fastino/gliner2.5-base-v1 8090 ;;
  all) start_one fastino/gliner2.5-base-v1 8090; start_one fastino/gliner2.5-multi-v1 8091
       start_one fastino/GLiNER2.5-Decide 8092; start_one fastino/gliner2.5-small-v1 8093 ;;
  *) start_one "$1" "${2:?port required}" ;;
esac

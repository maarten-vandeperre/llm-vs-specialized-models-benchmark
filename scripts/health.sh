#!/usr/bin/env bash
# Checks every inference endpoint and every benchmark candidate.
source "$(dirname "$0")/lib.sh"
set +e
printf '%-28s ' "Ollama $OLLAMA_URL"; curl -sf "$OLLAMA_URL/api/version" || echo "DOWN"; echo
for p in 8090 8091 8092 8093; do printf '%-28s ' "GLiNER :$p"; curl -sf "http://localhost:$p/health" | python3 -c 'import json,sys;d=json.load(sys.stdin);print(d["status"],list(d["models"]))' 2>/dev/null || echo "DOWN"; done
cd "$ROOT/backend"; [[ -f target/quarkus-app/quarkus-run.jar ]] || mvn -q package -DskipTests
java -jar target/quarkus-app/quarkus-run.jar health 2>/dev/null

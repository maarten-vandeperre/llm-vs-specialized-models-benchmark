#!/usr/bin/env bash
# Full methodology run on the evaluation split: 10 warm-up, 5 repetitions (latency sample 30),
# concurrency 1/4/8 with 40 requests each, cold starts. Runs only healthy candidates unless MODELS is set.
source "$(dirname "$0")/lib.sh"
cd "$ROOT/backend"; [[ -f target/quarkus-app/quarkus-run.jar ]] || mvn -q package -DskipTests
ARGS=(benchmark --warmup=10 --repetitions=5 --latency-sample=30 --concurrency=1,4,8 --throughput-requests=40 --cold-start=true --label=full)
[[ -n "${MODELS:-}" ]] && ARGS+=(--models="$MODELS")
exec java -jar target/quarkus-app/quarkus-run.jar "${ARGS[@]}" "$@"

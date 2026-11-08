#!/usr/bin/env bash
# Small stratified run (30 evaluation records, 2 warm-up, no throughput phase). Takes a few minutes.
source "$(dirname "$0")/lib.sh"
cd "$ROOT/backend"; [[ -f target/quarkus-app/quarkus-run.jar ]] || mvn -q package -DskipTests
MODELS="${MODELS:-kev-0.8b,kev-0.8b-candsel,gliner2.5-base,nuextract3,qwen3.5-4b,hybrid-gliner-kev-qwen,hybrid-gliner-qwen}"
exec java -jar target/quarkus-app/quarkus-run.jar benchmark --profile=smoke --models="$MODELS" --label=smoke "$@"

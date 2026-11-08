#!/usr/bin/env bash
# Derives hybrid routing thresholds from the CALIBRATION split only → results/calibration/thresholds.json
source "$(dirname "$0")/lib.sh"
cd "$ROOT/backend"; [[ -f target/quarkus-app/quarkus-run.jar ]] || mvn -q package -DskipTests
exec java -jar target/quarkus-app/quarkus-run.jar calibrate "$@"

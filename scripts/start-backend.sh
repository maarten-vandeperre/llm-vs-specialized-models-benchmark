#!/usr/bin/env bash
# Builds and starts the Quarkus backend (REST API on :8080).
source "$(dirname "$0")/lib.sh"
cd "$ROOT/backend"
mvn -q package -DskipTests
exec java -jar target/quarkus-app/quarkus-run.jar

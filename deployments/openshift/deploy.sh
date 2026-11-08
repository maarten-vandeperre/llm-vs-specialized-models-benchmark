#!/usr/bin/env bash
# Builds images in-cluster (binary builds) and deploys GLiNER + backend + dashboard to the given namespace.
# Does not delete or modify unrelated resources. Usage: deploy.sh <namespace>
set -euo pipefail
NS="${1:?namespace required (see: oc projects)}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
oc apply -n "$NS" -f "$ROOT/deployments/openshift/buildconfigs.yaml"
echo "== build backend (prebuilt quarkus-app)"
(cd "$ROOT/backend" && mvn -q package -DskipTests)
mkdir -p "$TMP/backend" && cp -r "$ROOT/backend/target/quarkus-app" "$TMP/backend/" && cp -r "$ROOT/datasets" "$TMP/backend/" \
  && cp "$ROOT/backend/Containerfile.prebuilt" "$TMP/backend/"
oc start-build beyond-llm-backend -n "$NS" --from-dir="$TMP/backend" --follow
echo "== build gliner"
mkdir -p "$TMP/gliner" && cp "$ROOT/inference/gliner/"{app.py,requirements.txt,Containerfile} "$TMP/gliner/"
oc start-build beyond-llm-gliner -n "$NS" --from-dir="$TMP/gliner" --follow
echo "== build frontend"
mkdir -p "$TMP/frontend" && (cd "$ROOT/frontend" && tar --exclude node_modules --exclude dist -cf - .) | tar -xf - -C "$TMP/frontend"
oc start-build beyond-llm-frontend -n "$NS" --from-dir="$TMP/frontend" --follow
echo "== apply manifests"
oc kustomize "$ROOT/deployments/openshift" | sed "s/REPLACE_NAMESPACE/$NS/g" | oc apply -n "$NS" -f -
# The Deployments reference ':latest' (pull policy Always). A rebuilt image does not change the pod spec, so restart
# explicitly to pick up the new images.
oc rollout restart deploy/beyond-llm-gliner deploy/beyond-llm-backend deploy/beyond-llm-frontend -n "$NS"
oc rollout status deploy/beyond-llm-gliner -n "$NS" --timeout=15m
oc rollout status deploy/beyond-llm-backend -n "$NS" --timeout=5m
oc rollout status deploy/beyond-llm-frontend -n "$NS" --timeout=5m
echo "Dashboard: https://$(oc get route beyond-llm-dashboard -n "$NS" -o jsonpath='{.spec.host}')"
echo "API:       https://$(oc get route beyond-llm-api -n "$NS" -o jsonpath='{.spec.host}')/api/models"

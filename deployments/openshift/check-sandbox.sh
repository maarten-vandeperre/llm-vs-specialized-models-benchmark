#!/usr/bin/env bash
# Read-only inspection of the target namespace BEFORE deploying (quota, limits, storage, GPUs, OpenShift AI, permissions).
set -uo pipefail
NS="${1:-$(oc project -q 2>/dev/null)}"
[[ -z "$NS" ]] && { echo "usage: $0 <namespace>   (see: oc projects)"; exit 1; }
echo "== Namespace: $NS  (user $(oc whoami), server $(oc whoami --show-server))"
echo "== ResourceQuota"; oc describe resourcequota -n "$NS" | grep -E "^Name:|cpu|memory|gpu|storage|persistentvolumeclaims"
echo "== LimitRange"; oc describe limitrange -n "$NS" | grep -E "Container|Type"
echo "== StorageClasses"; oc get storageclass
echo "== Existing workloads (will NOT be modified)"; oc get deploy,statefulset,svc,route,pvc -n "$NS" 2>/dev/null
echo "== GPU quota"; oc describe resourcequota -n "$NS" | grep -i gpu || echo "no GPU entries"
echo "== OpenShift AI / KServe"; oc api-resources 2>/dev/null | grep -E "serving.kserve.io" | awk '{print "  "$1}'
oc get templates -n redhat-ods-applications -o custom-columns=NAME:.metadata.name 2>/dev/null | sed 's/^/  runtime template: /' | head -20
echo "== Permissions"
for r in deployments services routes buildconfigs persistentvolumeclaims inferenceservices.serving.kserve.io servingruntimes.serving.kserve.io; do
  printf '  create %-40s %s\n' "$r" "$(oc auth can-i create "$r" -n "$NS")"; done

#!/usr/bin/env bash
# Detects the local environment (Profile A: MacBook). Prints findings; never installs anything.
source "$(dirname "$0")/lib.sh"
set +e
echo "== Platform"
if [[ "$(uname)" == "Darwin" ]]; then
  echo "macOS:          $(sw_vers -productVersion) ($(sw_vers -buildVersion))"
  echo "Chip:           $(sysctl -n machdep.cpu.brand_string)"
  echo "Unified memory: $(( $(sysctl -n hw.memsize) / 1073741824 )) GiB"
  echo "CPU count:      $(sysctl -n hw.ncpu) (P=$(sysctl -n hw.perflevel0.physicalcpu 2>/dev/null) E=$(sysctl -n hw.perflevel1.physicalcpu 2>/dev/null))"
  if [[ "$(uname -m)" == "arm64" ]]; then echo "MLX support:    yes (Apple Silicon) – required for kev:*-mlx-* tags"; else echo "MLX support:    no (Intel Mac)"; fi
else
  echo "OS:             $(uname -sr)"; echo "CPU count: $(nproc)"; echo "MLX support: no"
fi
echo "Free disk:      $(df -h "$ROOT" | awk 'NR==2{print $4}')"
echo
echo "== Tools"
java -version 2>&1 | head -1 | sed 's/^/java:    /'
mvn -v 2>/dev/null | head -1 | sed 's/^/maven:   /'
(command -v python3.12 >/dev/null && python3.12 --version || echo "python3.12 not found (uv can provide it)") | sed 's/^/python:  /'
(command -v uv >/dev/null && uv --version || echo "uv not found (optional)") | sed 's/^/uv:      /'
(command -v node >/dev/null && node --version || echo "node not found") | sed 's/^/node:    /'
(command -v podman >/dev/null && podman --version || echo "podman not found (optional)") | sed 's/^/podman:  /'
(command -v oc >/dev/null && oc version --client 2>/dev/null | head -1 || echo "oc not found (only for OpenShift)") | sed 's/^/oc:      /'
echo
echo "== Ollama"
if command -v ollama >/dev/null; then ollama --version 2>/dev/null | tail -1; fi
V=$(curl -sf "$OLLAMA_URL/api/version" | sed -E 's/.*"version":"([^"]+)".*/\1/')
if [[ -z "$V" ]]; then warn "Ollama server not reachable at $OLLAMA_URL (start Ollama.app or 'ollama serve')";
else
  echo "Server version: $V (Kev requires >= 0.40.4)"
  echo "Installed models:"; curl -sf "$OLLAMA_URL/api/tags" | python3 -c 'import json,sys;[print(f"  {m[\"name\"]:<34} {m[\"size\"]/1e9:5.1f} GB  {m[\"digest\"][:12]}") for m in json.load(sys.stdin)["models"]]'
fi

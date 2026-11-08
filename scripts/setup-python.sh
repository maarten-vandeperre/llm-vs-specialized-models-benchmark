#!/usr/bin/env bash
# Creates inference/gliner/.venv (Python 3.12) and installs the GLiNER service dependencies.
source "$(dirname "$0")/lib.sh"
cd "$ROOT/inference/gliner"
if command -v uv >/dev/null; then
  uv venv --python 3.12 .venv
  uv pip install --python .venv/bin/python -r requirements.txt -r requirements-dev.txt
else
  python3.12 -m venv .venv
  .venv/bin/pip install -r requirements.txt -r requirements-dev.txt
fi
.venv/bin/python -m pytest -q tests

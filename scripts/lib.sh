#!/usr/bin/env bash
# Shared helpers for the benchmark scripts.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$ROOT/results/logs"
mkdir -p "$LOG_DIR"
OLLAMA_URL="${OLLAMA_URL:-http://localhost:11434}"
info() { printf '\033[1;34m[info]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31m[fail]\033[0m %s\n' "$*"; exit 1; }

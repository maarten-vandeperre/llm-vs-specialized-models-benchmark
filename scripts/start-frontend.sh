#!/usr/bin/env bash
# Starts the React dashboard (Vite dev server on :5173, proxies /api to :8080).
source "$(dirname "$0")/lib.sh"
cd "$ROOT/frontend"
[[ -d node_modules ]] || npm ci
exec npm run dev

#!/usr/bin/env bash
# Stops natively started GLiNER services.
pkill -f "uvicorn app:app" && echo "stopped" || echo "no GLiNER service running"

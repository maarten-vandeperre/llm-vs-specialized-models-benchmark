#!/usr/bin/env bash
# Pulls benchmark models into Ollama. Usage: pull-models.sh [minimal|large]
#   minimal (default): kev:0.8b-mlx-bf16 (1.5 GB), qwen3.5:4b (~4.0 GB), numind/nuextract3:q4_k_m (~3.4 GB)
#   large:             minimal + kev:4b-mlx-bf16 (8.4 GB) + gemma4:12b (~7.7 GB, gemma4-12b-decision)
source "$(dirname "$0")/lib.sh"
PROFILE="${1:-minimal}"
MODELS=(kev:0.8b-mlx-bf16 qwen3.5:4b numind/nuextract3:q4_k_m)
NEED_GB=10
if [[ "$PROFILE" == "large" ]]; then MODELS+=(kev:4b-mlx-bf16 gemma4:12b); NEED_GB=27; fi
FREE_GB=$(df -g "$ROOT" 2>/dev/null | awk 'NR==2{print $4}' || df -BG "$ROOT" | awk 'NR==2{gsub("G","",$4);print $4}')
info "Profile $PROFILE needs ~${NEED_GB} GB; free: ${FREE_GB} GB"
[[ "$FREE_GB" -lt "$NEED_GB" ]] && fail "Not enough disk space for profile $PROFILE"
curl -sf "$OLLAMA_URL/api/version" >/dev/null || fail "Ollama not reachable at $OLLAMA_URL"
for m in "${MODELS[@]}"; do info "ollama pull $m"; ollama pull "$m"; done
ollama list

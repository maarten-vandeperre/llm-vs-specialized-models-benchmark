#!/usr/bin/env python3
"""Compute the API cost of commercial-model runs from the provider-reported token counts in predictions.jsonl.

Usage: scripts/cloud-cost.py <runId> [<runId> ...]
Prices: USD per 1M tokens, standard tier, read from the providers' pricing pages on 2026-11-08:
  https://developers.openai.com/api/docs/pricing and https://platform.claude.com/docs/en/about-claude/pricing
All prompts here are far below the long-context price tiers. Every phase (warm-up, quality, latency, throughput) counts.
"""
import json, re, sys
from collections import defaultdict
from pathlib import Path

PRICES = {  # model id: (input, cached input, output)
    "openai-gpt-6-luna": (0.10, 0.01, 0.50),
    "openai-gpt-6.1-sol": (2.00, 0.10, 10.00),
    "claude-haiku-5.5": (0.10, 0.01, 0.50),
    "claude-opus-5.5": (4.00, 0.20, 20.00),
}
CACHED = re.compile(r'"cached_tokens\\*"\s*:\s*(\d+)')
ROOT = Path(__file__).resolve().parent.parent

print("| Model | calls | input tok | (cached) | output tok | mean in / out per call | cost USD | USD per 1,000 calls |")
print("|---|---|---|---|---|---|---|---|")
for run in sys.argv[1:]:
    agg = defaultdict(lambda: [0, 0, 0, 0])
    for line in open(ROOT / "results" / "runs" / run / "predictions.jsonl"):
        p = json.loads(line)
        if p["modelId"] not in PRICES:
            continue
        t = p.get("timing") or {}
        a = agg[p["modelId"]]
        a[0] += 1
        a[1] += t.get("inputTokens") or 0
        a[3] += t.get("outputTokens") or 0
        m = CACHED.search(p.get("raw") or "")
        a[2] += int(m.group(1)) if m else 0
    for model, (n, tin, cached, tout) in agg.items():
        pi, pc, po = PRICES[model]
        cost = ((tin - cached) * pi + cached * pc + tout * po) / 1e6
        print(f"| {model} | {n} | {tin:,} | {cached:,} | {tout:,} | {tin / n:.0f} / {tout / n:.0f} | {cost:.2f} | {cost / n * 1000:.2f} |")

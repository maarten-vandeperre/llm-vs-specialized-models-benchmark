# Benchmark report 20261110-105249-3ead

> **Synthetic benchmark.** Synthetic, fictional coffee-shop dataset. Results do not generalize to production customers.

- Status: COMPLETED, created 2026-11-10T10:52:49.714355Z, finished 2026-11-10T10:52:59.770620Z
- Dataset: `coffee-v1` split `evaluation`, groups [classification], limit 20, records selected 20, sha256 `627e6d79f1b9c54031c6f9c421686e108c133d2327338c87956cc99db631d49d`
- Method: warm-up 10, repetitions 5, latency sample 30, concurrency [1, 4, 8], throughput requests 40, cold start true, seed 42, timeout 180 s
- Model order (seeded shuffle): [decisio-gemma-12b, kev-0.8b]
- Hardware: Apple M1 Max, 64.00 GiB memory, 10 CPUs, Mac OS X 26.6.2
- Software: Java 25.0.2, Quarkus 3.40.1.1, Ollama 0.40.15, GLiNER service {python=3.12.14, gliner2=null, torch=null, transformers=null}
- Hybrid thresholds: {gliner=0.9649, source=/Users/maartenvandeperre/workspace/redhat/llm-vs-specialized-models-benchmark/results/calibration/thresholds.json (calibration split), kev=0.5862}

## ⚠ Warnings

- decisio-gemma-12b: 100.0% of measured requests failed with INFRA_ERROR/TIMEOUT. Latency and throughput for this model are computed on successful requests only and may be unrepresentative.

## Quality (quality phase, failures counted as errors)

| Model | n | Intent acc [95% CI] | Intent macro-F1 [CI] | Mean field acc | Extraction EM [CI] | Entity F1 | Halluc. rate | Missing rate | Decision acc | Failures (unsup/malf/timeout/infra) |
|---|---|---|---|---|---|---|---|---|---|---|
| decisio-gemma-12b | 20 | 0.0% [0.0%–16.1%] | 0.00 [0.00–0.00] | 0.0% | – | – | – | – | – | 0/0/0/20 |
| kev-0.8b | 20 | 85.0% [64.0%–94.8%] | 0.77 [0.65–1.00] | 75.0% | – | – | – | – | – | 0/0/0/0 |

## Per-field classification accuracy

| Model | intent | sentiment | category | complaint | all four correct |
|---|---|---|---|---|---|
| decisio-gemma-12b | 0.0% | 0.0% | 0.0% | 0.0% | 0.0% |
| kev-0.8b | 85.0% | 75.0% | 60.0% | 80.0% | 25.0% |

## Extraction field accuracy

| Model | orderId | items | total | currency | offset acc (n) |
|---|---|---|---|---|---|

## Latency (client-side, concurrency 1)

Headline columns: each evaluation input sent once (quality phase), successful requests, full prompt-cache hits excluded. "repeat p50" is the latency-phase median over repeated identical inputs, which a runtime may serve from its prompt cache.

| Model | n | p50 ms [CI] | p90 | p95 | p99 | server p50 ms | cold start ms (load ms) | out tok/s | repeat p50 ms | cache full hits (first-seen / repeat) |
|---|---|---|---|---|---|---|---|---|---|---|
| decisio-gemma-12b | 0 | – | – | – | – | – | – | – | – | – |
| kev-0.8b | 19 | 164.30 [161.74–168.39] | 183.92 | 216.61 | n<100 | n/a | 856.30 (n/a) | n/a | 9.40 | 1 / 80 of 80 |

## Throughput

Each concurrency level uses its own window of inputs not used by the warm-up or latency phases.

| Model | concurrency | requests | wall s | req/s | p50 ms | p95 ms | errors | cache full hits |
|---|---|---|---|---|---|---|---|---|
| decisio-gemma-12b | 1 | 40 | 0.11 | 0.00 | n/a | n/a | 40 | 0 |
| decisio-gemma-12b | 4 | 40 | 0.03 | 0.00 | n/a | n/a | 40 | 0 |
| decisio-gemma-12b | 8 | 40 | 0.02 | 0.00 | n/a | n/a | 40 | 0 |
| kev-0.8b | 1 | 40 | 0.45 | 88.11 | 8.65 | 13.47 | 0 | 40 |
| kev-0.8b | 4 | 40 | 0.22 | 186.05 | 19.41 | 25.04 | 0 | 40 |
| kev-0.8b | 8 | 40 | 0.24 | 164.61 | 43.67 | 58.60 | 0 | 40 |

## Confidence calibration (native scores only)

| Model | n | Brier | ECE | risk @0.80 (coverage) | note |
|---|---|---|---|---|---|
| decisio-gemma-12b | – | – | – | – | provider returns no native label probabilities (none are invented) |
| kev-0.8b | 80 | 0.17 | 0.15 | 0.0% (21.3%) | top-label score |

## Abstention on ambiguous inputs

| Model | n ambiguous | predicted `none` | false-positive decisions | overall coverage |
|---|---|---|---|---|

## Per language (intent accuracy / extraction exact match)

| Model | en | nl | fr |
|---|---|---|---|
| decisio-gemma-12b | 0.0% / n/a (n=14) | 0.0% / n/a (n=4) | 0.0% / n/a (n=2) |
| kev-0.8b | 85.7% / n/a (n=14) | 100.0% / n/a (n=4) | 50.0% / n/a (n=2) |

## Input length (long group, extraction exact match / latency p50 ms / unsupported)

| Model | tokens | position | n | EM | p50 ms | unsupported |
|---|---|---|---|---|---|---|

## Resources (sampled during this model's phases)

| Model | RSS mean MB | RSS peak MB | CPU mean % | CPU peak % | Ollama loaded size | storage | note |
|---|---|---|---|---|---|---|---|
| decisio-gemma-12b | 116.20 | 116.20 | 9.90 | 9.90 | n/a | 7.14 GiB | ps sampling every 500 ms; CPU% is per-core percentage summed over matched processes (can exceed 100) |
| kev-0.8b | 1304.30 | 1720.20 | 73.10 | 142.60 | kev:0.8b-mlx-bf16: 6.18 GiB | 1.42 GiB | ps sampling every 500 ms; CPU% is per-core percentage summed over matched processes (can exceed 100) |

GPU utilization and energy: not measured (no reliable unprivileged method on this platform).

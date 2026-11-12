# Benchmark report 20261110-110607-52e6

> **Synthetic benchmark.** Synthetic, fictional coffee-shop dataset. Results do not generalize to production customers.

- Status: COMPLETED, created 2026-11-10T11:06:07.177248Z, finished 2026-11-10T11:36:42.937567Z
- Dataset: `coffee-v1` split `evaluation`, groups all, limit null, records selected 693, sha256 `627e6d79f1b9c54031c6f9c421686e108c133d2327338c87956cc99db631d49d`
- Method: warm-up 10, repetitions 5, latency sample 30, concurrency [1, 4, 8], throughput requests 40, cold start true, seed 42, timeout 180 s
- Model order (seeded shuffle): [gemma4-12b-decision, kev-0.8b]
- Hardware: Apple M1 Max, 64.00 GiB memory, 10 CPUs, Mac OS X 26.6.2
- Software: Java 25.0.2, Quarkus 3.40.1.1, Ollama 0.40.15, GLiNER service {python=3.12.14, gliner2=null, torch=null, transformers=null}
- Hybrid thresholds: {gliner=0.9649, source=/Users/maartenvandeperre/workspace/redhat/llm-vs-specialized-models-benchmark/results/calibration/thresholds.json (calibration split), kev=0.5862}

## Quality (quality phase, failures counted as errors)

| Model | n | Intent acc [95% CI] | Intent macro-F1 [CI] | Mean field acc | Extraction EM [CI] | Entity F1 | Halluc. rate | Missing rate | Decision acc | Failures (unsup/malf/timeout/infra) |
|---|---|---|---|---|---|---|---|---|---|---|
| gemma4-12b-decision | 446 | 85.7% [82.1%–88.6%] | 0.84 [0.82–0.87] | 87.3% | – | – | – | – | – | 0/0/0/0 |
| kev-0.8b | 446 | 70.0% [65.5%–74.0%] | 0.75 [0.72–0.78] | 79.4% | – | – | – | – | – | 0/0/0/0 |

## Per-field classification accuracy

| Model | intent | sentiment | category | complaint | all four correct |
|---|---|---|---|---|---|
| gemma4-12b-decision | 85.7% | 89.9% | 80.5% | 93.1% | 66.4% |
| kev-0.8b | 70.0% | 86.3% | 74.2% | 87.2% | 38.3% |

## Extraction field accuracy

| Model | orderId | items | total | currency | offset acc (n) |
|---|---|---|---|---|---|

## Latency (client-side, concurrency 1)

Headline columns: each evaluation input sent once (quality phase), successful requests, full prompt-cache hits excluded. "repeat p50" is the latency-phase median over repeated identical inputs, which a runtime may serve from its prompt cache.

| Model | n | p50 ms [CI] | p90 | p95 | p99 | server p50 ms | cold start ms (load ms) | out tok/s | repeat p50 ms | cache full hits (first-seen / repeat) |
|---|---|---|---|---|---|---|---|---|---|---|
| gemma4-12b-decision | 432 | 2540.92 [2525.08–2557.47] | 2691.59 | 2710.08 | 2747.80 | 2534.10 | 5875.01 (3371.32) | n/a | 2482.61 | 14 / 0 of 120 |
| kev-0.8b | 421 | 185.68 [184.50–186.89] | 201.34 | 205.43 | 213.74 | n/a | 725.57 (n/a) | n/a | 5.05 | 25 / 90 of 120 |

## Throughput

Each concurrency level uses its own window of inputs not used by the warm-up or latency phases.

| Model | concurrency | requests | wall s | req/s | p50 ms | p95 ms | errors | cache full hits |
|---|---|---|---|---|---|---|---|---|
| gemma4-12b-decision | 1 | 40 | 97.99 | 0.41 | 2408.15 | 2560.20 | 0 | 0 |
| gemma4-12b-decision | 4 | 40 | 100.10 | 0.40 | 9927.91 | 10465.51 | 0 | 0 |
| gemma4-12b-decision | 8 | 40 | 96.42 | 0.41 | 19578.32 | 19898.89 | 0 | 1 |
| kev-0.8b | 1 | 40 | 7.17 | 5.58 | 181.36 | 189.69 | 0 | 1 |
| kev-0.8b | 4 | 40 | 7.55 | 5.30 | 754.58 | 793.11 | 0 | 0 |
| kev-0.8b | 8 | 40 | 6.99 | 5.72 | 1434.75 | 1466.11 | 0 | 2 |

## Confidence calibration (native scores only)

| Model | n | Brier | ECE | risk @0.80 (coverage) | note |
|---|---|---|---|---|---|
| gemma4-12b-decision | 1784 | 0.12 | 0.12 | 11.7% (97.8%) | top-label score |
| kev-0.8b | 1784 | 0.17 | 0.20 | 3.2% (16.0%) | top-label score |

## Abstention on ambiguous inputs

| Model | n ambiguous | predicted `none` | false-positive decisions | overall coverage |
|---|---|---|---|---|
| gemma4-12b-decision | 64 | 14.1% | 85.9% | 97.5% |
| kev-0.8b | 64 | 21.9% | 78.1% | 84.5% |

## Per language (intent accuracy / extraction exact match)

| Model | en | nl | fr |
|---|---|---|---|
| gemma4-12b-decision | 88.9% / n/a (n=261) | 82.0% / n/a (n=89) | 80.2% / n/a (n=96) |
| kev-0.8b | 74.0% / n/a (n=261) | 62.9% / n/a (n=89) | 65.6% / n/a (n=96) |

## Input length (long group, extraction exact match / latency p50 ms / unsupported)

| Model | tokens | position | n | EM | p50 ms | unsupported |
|---|---|---|---|---|---|---|

## Resources (sampled during this model's phases)

| Model | RSS mean MB | RSS peak MB | CPU mean % | CPU peak % | Ollama loaded size | storage | note |
|---|---|---|---|---|---|---|---|
| gemma4-12b-decision | 7524.10 | 7686.60 | 22.30 | 133.00 | gemma4:12b: 20.95 GiB | 7.18 GiB | ps sampling every 500 ms; CPU% is per-core percentage summed over matched processes (can exceed 100) |
| kev-0.8b | 1389.90 | 1408.50 | 68.00 | 109.00 | kev:0.8b-mlx-bf16: 9.46 GiB | 1.42 GiB | ps sampling every 500 ms; CPU% is per-core percentage summed over matched processes (can exceed 100) |

GPU utilization and energy: not measured (no reliable unprivileged method on this platform).

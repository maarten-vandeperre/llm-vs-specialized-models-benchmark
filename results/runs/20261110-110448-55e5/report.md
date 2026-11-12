# Benchmark report 20261110-110448-55e5

> **Synthetic benchmark.** Synthetic, fictional coffee-shop dataset. Results do not generalize to production customers.

- Status: COMPLETED, created 2026-11-10T11:04:48.675071Z, finished 2026-11-10T11:05:45.906156Z
- Dataset: `coffee-v1` split `evaluation`, groups [classification], limit 20, records selected 20, sha256 `627e6d79f1b9c54031c6f9c421686e108c133d2327338c87956cc99db631d49d`
- Method: warm-up 2, repetitions 1, latency sample 5, concurrency [1], throughput requests 5, cold start true, seed 42, timeout 180 s
- Model order (seeded shuffle): [gemma4-12b-decision]
- Hardware: Apple M1 Max, 64.00 GiB memory, 10 CPUs, Mac OS X 26.6.2
- Software: Java 25.0.2, Quarkus 3.40.1.1, Ollama 0.40.15, GLiNER service {python=3.12.14, gliner2=null, torch=null, transformers=null}
- Hybrid thresholds: {kev=0.5862, gliner=0.9649, source=/Users/maartenvandeperre/workspace/redhat/llm-vs-specialized-models-benchmark/results/calibration/thresholds.json (calibration split)}

## Quality (quality phase, failures counted as errors)

| Model | n | Intent acc [95% CI] | Intent macro-F1 [CI] | Mean field acc | Extraction EM [CI] | Entity F1 | Halluc. rate | Missing rate | Decision acc | Failures (unsup/malf/timeout/infra) |
|---|---|---|---|---|---|---|---|---|---|---|
| gemma4-12b-decision | 20 | 95.0% [76.4%–99.1%] | 0.82 [0.75–1.00] | 88.8% | – | – | – | – | – | 0/0/0/0 |

## Per-field classification accuracy

| Model | intent | sentiment | category | complaint | all four correct |
|---|---|---|---|---|---|
| gemma4-12b-decision | 95.0% | 80.0% | 85.0% | 95.0% | 65.0% |

## Extraction field accuracy

| Model | orderId | items | total | currency | offset acc (n) |
|---|---|---|---|---|---|

## Latency (client-side, concurrency 1)

Headline columns: each evaluation input sent once (quality phase), successful requests, full prompt-cache hits excluded. "repeat p50" is the latency-phase median over repeated identical inputs, which a runtime may serve from its prompt cache.

| Model | n | p50 ms [CI] | p90 | p95 | p99 | server p50 ms | cold start ms (load ms) | out tok/s | repeat p50 ms | cache full hits (first-seen / repeat) |
|---|---|---|---|---|---|---|---|---|---|---|
| gemma4-12b-decision | 19 | 2379.27 [2332.55–2442.86] | 2488.40 | 2496.50 | n<100 | 2373.09 | 4497.44 (1958.07) | n/a | n/a | 1 / 0 of 0 |

## Throughput

Each concurrency level uses its own window of inputs not used by the warm-up or latency phases.

| Model | concurrency | requests | wall s | req/s | p50 ms | p95 ms | errors | cache full hits |
|---|---|---|---|---|---|---|---|---|
| gemma4-12b-decision | 1 | 5 | 1.85 | 2.71 | 365.86 | 389.13 | 0 | 5 |

## Confidence calibration (native scores only)

| Model | n | Brier | ECE | risk @0.80 (coverage) | note |
|---|---|---|---|---|---|
| gemma4-12b-decision | 80 | 0.10 | 0.10 | 10.3% (97.5%) | top-label score |

## Abstention on ambiguous inputs

| Model | n ambiguous | predicted `none` | false-positive decisions | overall coverage |
|---|---|---|---|---|

## Per language (intent accuracy / extraction exact match)

| Model | en | nl | fr |
|---|---|---|---|
| gemma4-12b-decision | 100.0% / n/a (n=14) | 100.0% / n/a (n=4) | 50.0% / n/a (n=2) |

## Input length (long group, extraction exact match / latency p50 ms / unsupported)

| Model | tokens | position | n | EM | p50 ms | unsupported |
|---|---|---|---|---|---|---|

## Resources (sampled during this model's phases)

| Model | RSS mean MB | RSS peak MB | CPU mean % | CPU peak % | Ollama loaded size | storage | note |
|---|---|---|---|---|---|---|---|
| gemma4-12b-decision | 7370.00 | 7613.10 | 25.20 | 173.00 | gemma4:12b: 21.06 GiB | 7.18 GiB | ps sampling every 500 ms; CPU% is per-core percentage summed over matched processes (can exceed 100) |

GPU utilization and energy: not measured (no reliable unprivileged method on this platform).

# Benchmark report 20261110-111019-5216

> **Synthetic benchmark.** Synthetic, fictional coffee-shop dataset. Results do not generalize to production customers.

- Status: COMPLETED, created 2026-11-10T11:10:19.028765986Z, finished 2026-11-10T11:23:34.829533088Z
- Dataset: `coffee-v1` split `evaluation`, groups all, limit 160, records selected 160, sha256 `627e6d79f1b9c54031c6f9c421686e108c133d2327338c87956cc99db631d49d`
- Method: warm-up 2, repetitions 1, latency sample 10, concurrency [1], throughput requests 10, cold start true, seed 42, timeout 300 s
- Model order (seeded shuffle): [gemma4-12b-decision]
- Hardware: Intel(R) Xeon(R) Platinum 8259CL CPU @ 2.50GHz, n/a memory, 1 CPUs, Linux 5.14.0-570.135.1.el9_6.x86_64
- Software: Java 25.0.5, Quarkus 3.40.1.1, Ollama 0.40.15, GLiNER service null
- Hybrid thresholds: {kev=0.8, gliner=0.8, source=configuration defaults}

## ⚠ Warnings

- gemma4-12b-decision: 89.0% of measured requests failed with INFRA_ERROR/TIMEOUT. Latency and throughput for this model are computed on successful requests only and may be unrepresentative.

## Quality (quality phase, failures counted as errors)

| Model | n | Intent acc [95% CI] | Intent macro-F1 [CI] | Mean field acc | Extraction EM [CI] | Entity F1 | Halluc. rate | Missing rate | Decision acc | Failures (unsup/malf/timeout/infra) |
|---|---|---|---|---|---|---|---|---|---|---|
| gemma4-12b-decision | 107 | 9.4% [5.2%–16.4%] | 0.13 [0.06–0.18] | 8.6% | – | – | – | – | – | 0/0/0/95 |

## Per-field classification accuracy

| Model | intent | sentiment | category | complaint | all four correct |
|---|---|---|---|---|---|
| gemma4-12b-decision | 9.4% | 8.4% | 6.5% | 10.3% | 5.6% |

## Extraction field accuracy

| Model | orderId | items | total | currency | offset acc (n) |
|---|---|---|---|---|---|

## Latency (client-side, concurrency 1)

Headline columns: each evaluation input sent once (quality phase), successful requests, full prompt-cache hits excluded. "repeat p50" is the latency-phase median over repeated identical inputs, which a runtime may serve from its prompt cache.

| Model | n | p50 ms [CI] | p90 | p95 | p99 | server p50 ms | cold start ms (load ms) | out tok/s | repeat p50 ms | cache full hits (first-seen / repeat) |
|---|---|---|---|---|---|---|---|---|---|---|
| gemma4-12b-decision | 11 | 53203.79 [50239.68–57964.11] | 58160.88 | 58938.76 | n<100 | 53185.60 | 60271.76 (3.35) | n/a | n/a | 1 / 0 of 0 |

## Throughput

Each concurrency level uses its own window of inputs not used by the warm-up or latency phases.

| Model | concurrency | requests | wall s | req/s | p50 ms | p95 ms | errors | cache full hits |
|---|---|---|---|---|---|---|---|---|
| gemma4-12b-decision | 1 | 10 | 0.07 | 0.00 | n/a | n/a | 10 | 0 |

## Confidence calibration (native scores only)

| Model | n | Brier | ECE | risk @0.80 (coverage) | note |
|---|---|---|---|---|---|
| gemma4-12b-decision | 48 | 0.22 | 0.22 | 21.7% (95.8%) | top-label score |

## Abstention on ambiguous inputs

| Model | n ambiguous | predicted `none` | false-positive decisions | overall coverage |
|---|---|---|---|---|
| gemma4-12b-decision | 27 | 3.7% | 7.4% | 10.3% |

## Per language (intent accuracy / extraction exact match)

| Model | en | nl | fr |
|---|---|---|---|
| gemma4-12b-decision | 9.1% / n/a (n=55) | 10.3% / n/a (n=29) | 8.7% / n/a (n=23) |

## Input length (long group, extraction exact match / latency p50 ms / unsupported)

| Model | tokens | position | n | EM | p50 ms | unsupported |
|---|---|---|---|---|---|---|

## Resources (sampled during this model's phases)

| Model | RSS mean MB | RSS peak MB | CPU mean % | CPU peak % | Ollama loaded size | storage | note |
|---|---|---|---|---|---|---|---|
| gemma4-12b-decision | n/a | n/a | n/a | n/a | n/a | n/a | ps unavailable: Cannot run program "ps": Exec failed, error: 2 (No such file or directory)  |

GPU utilization and energy: not measured (no reliable unprivileged method on this platform).

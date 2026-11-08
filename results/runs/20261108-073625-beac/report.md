# Benchmark report 20261108-073625-beac

> **Synthetic benchmark.** Synthetic, fictional coffee-shop dataset. Results do not generalize to production customers.

- Status: COMPLETED, created 2026-11-08T07:36:25.897777Z, finished 2026-11-08T07:39:00.205259Z
- Dataset: `coffee-v1` split `evaluation`, groups all, limit 20, records selected 20, sha256 `627e6d79f1b9c54031c6f9c421686e108c133d2327338c87956cc99db631d49d`
- Method: warm-up 1, repetitions 1, latency sample 5, concurrency [1], throughput requests 5, cold start false, seed 42, timeout 180 s
- Model order (seeded shuffle): [claude-opus-5.5, openai-gpt-6.1-sol, openai-gpt-6-luna, claude-haiku-5.5]
- Hardware: Apple M1 Max, 64.00 GiB memory, 10 CPUs, Mac OS X 26.6.2
- Software: Java 25.0.2, Quarkus 3.40.1.1, Ollama 0.40.15, GLiNER service {python=3.12.14, gliner2=2.1.0, torch=2.15.0, transformers=5.22.2}
- Hybrid thresholds: {source=/Users/maartenvandeperre/workspace/redhat/temp-workshop/benchmark-platform/results/calibration/thresholds.json (calibration split), kev=0.5862, gliner=0.9649}

## Quality (quality phase, failures counted as errors)

| Model | n | Intent acc [95% CI] | Intent macro-F1 [CI] | Mean field acc | Extraction EM [CI] | Entity F1 | Halluc. rate | Missing rate | Decision acc | Failures (unsup/malf/timeout/infra) |
|---|---|---|---|---|---|---|---|---|---|---|
| claude-opus-5.5 | 20 | 92.3% [66.7%–98.6%] | 0.93 [0.65–1.00] | 98.1% | 100.0% [77.2%–100.0%] | 1.00 | 0.0% | 0.0% | 100.0% | 0/0/0/0 |
| openai-gpt-6.1-sol | 20 | 84.6% [57.8%–95.7%] | 0.84 [0.60–1.00] | 92.3% | 100.0% [77.2%–100.0%] | 1.00 | 0.0% | 0.0% | 100.0% | 0/0/0/0 |
| openai-gpt-6-luna | 20 | 84.6% [57.8%–95.7%] | 0.84 [0.60–1.00] | 88.5% | 100.0% [77.2%–100.0%] | 1.00 | 0.0% | 0.0% | 100.0% | 0/0/0/0 |
| claude-haiku-5.5 | 20 | 84.6% [57.8%–95.7%] | 0.88 [0.62–1.00] | 90.4% | 100.0% [77.2%–100.0%] | 1.00 | 0.0% | 0.0% | 83.3% | 0/0/0/0 |

## Per-field classification accuracy

| Model | intent | sentiment | category | complaint | all four correct |
|---|---|---|---|---|---|
| claude-opus-5.5 | 92.3% | 100.0% | 100.0% | 100.0% | 92.3% |
| openai-gpt-6.1-sol | 84.6% | 100.0% | 92.3% | 92.3% | 84.6% |
| openai-gpt-6-luna | 84.6% | 100.0% | 76.9% | 92.3% | 69.2% |
| claude-haiku-5.5 | 84.6% | 100.0% | 84.6% | 92.3% | 76.9% |

## Extraction field accuracy

| Model | orderId | items | total | currency | offset acc (n) |
|---|---|---|---|---|---|
| claude-opus-5.5 | 100.0% | 100.0% | 100.0% | 100.0% | n/a |
| openai-gpt-6.1-sol | 100.0% | 100.0% | 100.0% | 100.0% | n/a |
| openai-gpt-6-luna | 100.0% | 100.0% | 100.0% | 100.0% | n/a |
| claude-haiku-5.5 | 100.0% | 100.0% | 100.0% | 100.0% | n/a |

## Latency (client-side, concurrency 1)

Headline columns: each evaluation input sent once (quality phase), successful requests, full prompt-cache hits excluded. "repeat p50" is the latency-phase median over repeated identical inputs, which a runtime may serve from its prompt cache.

| Model | n | p50 ms [CI] | p90 | p95 | p99 | server p50 ms | cold start ms (load ms) | out tok/s | repeat p50 ms | cache full hits (first-seen / repeat) |
|---|---|---|---|---|---|---|---|---|---|---|
| claude-opus-5.5 | 20 | 1916.17 [1607.43–2148.80] | 2412.55 | 3015.03 | n<100 | n/a | n/a | n/a | n/a | 0 / 0 of 0 |
| openai-gpt-6.1-sol | 20 | 1739.36 [1527.84–1896.49] | 2112.60 | 2291.18 | n<100 | n/a | n/a | n/a | n/a | 0 / 0 of 0 |
| openai-gpt-6-luna | 20 | 1026.96 [949.96–1188.97] | 1335.20 | 1367.08 | n<100 | n/a | n/a | n/a | n/a | 0 / 0 of 0 |
| claude-haiku-5.5 | 20 | 1075.25 [1016.31–1139.09] | 1404.49 | 1573.64 | n<100 | n/a | n/a | n/a | n/a | 0 / 0 of 0 |

## Throughput

Each concurrency level uses its own window of inputs not used by the warm-up or latency phases.

| Model | concurrency | requests | wall s | req/s | p50 ms | p95 ms | errors | cache full hits |
|---|---|---|---|---|---|---|---|---|
| claude-opus-5.5 | 1 | 5 | 7.96 | 0.63 | 1268.22 | 2151.36 | 0 | 0 |
| openai-gpt-6.1-sol | 1 | 5 | 7.10 | 0.70 | 1453.96 | 1667.89 | 0 | 0 |
| openai-gpt-6-luna | 1 | 5 | 5.13 | 0.98 | 1004.50 | 1124.04 | 0 | 0 |
| claude-haiku-5.5 | 1 | 5 | 4.25 | 1.18 | 873.13 | 1001.20 | 0 | 0 |

## Confidence calibration (native scores only)

| Model | n | Brier | ECE | risk @0.80 (coverage) | note |
|---|---|---|---|---|---|
| claude-opus-5.5 | – | – | – | – | provider returns no native label probabilities (none are invented) |
| openai-gpt-6.1-sol | – | – | – | – | provider returns no native label probabilities (none are invented) |
| openai-gpt-6-luna | – | – | – | – | provider returns no native label probabilities (none are invented) |
| claude-haiku-5.5 | – | – | – | – | provider returns no native label probabilities (none are invented) |

## Abstention on ambiguous inputs

| Model | n ambiguous | predicted `none` | false-positive decisions | overall coverage |
|---|---|---|---|---|
| claude-opus-5.5 | 3 | 66.7% | 33.3% | 84.6% |
| openai-gpt-6.1-sol | 3 | 33.3% | 66.7% | 92.3% |
| openai-gpt-6-luna | 3 | 33.3% | 66.7% | 92.3% |
| claude-haiku-5.5 | 3 | 66.7% | 33.3% | 76.9% |

## Per language (intent accuracy / extraction exact match)

| Model | en | nl | fr |
|---|---|---|---|
| claude-opus-5.5 | 100.0% / 100.0% (n=10) | 100.0% / 100.0% (n=6) | 66.7% / 100.0% (n=4) |
| openai-gpt-6.1-sol | 100.0% / 100.0% (n=10) | 80.0% / 100.0% (n=6) | 66.7% / 100.0% (n=4) |
| openai-gpt-6-luna | 100.0% / 100.0% (n=10) | 80.0% / 100.0% (n=6) | 66.7% / 100.0% (n=4) |
| claude-haiku-5.5 | 100.0% / 100.0% (n=10) | 100.0% / 100.0% (n=6) | 33.3% / 100.0% (n=4) |

## Input length (long group, extraction exact match / latency p50 ms / unsupported)

| Model | tokens | position | n | EM | p50 ms | unsupported |
|---|---|---|---|---|---|---|
| claude-opus-5.5 | 100 | start | 3 | 100.0% | 1694.22 | 0 |
| openai-gpt-6.1-sol | 100 | start | 3 | 100.0% | 1470.67 | 0 |
| openai-gpt-6-luna | 100 | start | 3 | 100.0% | 1152.84 | 0 |
| claude-haiku-5.5 | 100 | start | 3 | 100.0% | 1139.09 | 0 |

## Resources (sampled during this model's phases)

| Model | RSS mean MB | RSS peak MB | CPU mean % | CPU peak % | Ollama loaded size | storage | note |
|---|---|---|---|---|---|---|---|
| claude-opus-5.5 | n/a | n/a | n/a | n/a | n/a | n/a | not sampled (pipeline or remote endpoint; see stage models) |
| openai-gpt-6.1-sol | n/a | n/a | n/a | n/a | n/a | n/a | not sampled (pipeline or remote endpoint; see stage models) |
| openai-gpt-6-luna | n/a | n/a | n/a | n/a | n/a | n/a | not sampled (pipeline or remote endpoint; see stage models) |
| claude-haiku-5.5 | n/a | n/a | n/a | n/a | n/a | n/a | not sampled (pipeline or remote endpoint; see stage models) |

GPU utilization and energy: not measured (no reliable unprivileged method on this platform).

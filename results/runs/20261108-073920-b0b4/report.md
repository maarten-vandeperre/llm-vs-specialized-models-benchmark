# Benchmark report 20261108-073920-b0b4

> **Synthetic benchmark.** Synthetic, fictional coffee-shop dataset. Results do not generalize to production customers.

- Status: COMPLETED, created 2026-11-08T07:39:20.361932Z, finished 2026-11-08T08:20:30.985806Z
- Dataset: `coffee-v1` split `evaluation`, groups all, limit null, records selected 693, sha256 `627e6d79f1b9c54031c6f9c421686e108c133d2327338c87956cc99db631d49d`
- Method: warm-up 10, repetitions 5, latency sample 30, concurrency [1, 4, 8], throughput requests 40, cold start true, seed 42, timeout 180 s
- Model order (seeded shuffle): [openai-gpt-6-luna, openai-gpt-6.1-sol]
- Hardware: Apple M1 Max, 64.00 GiB memory, 10 CPUs, Mac OS X 26.6.2
- Software: Java 25.0.2, Quarkus 3.40.1.1, Ollama 0.40.15, GLiNER service {python=3.12.14, gliner2=2.1.0, torch=2.15.0, transformers=5.22.2}
- Hybrid thresholds: {source=/Users/maartenvandeperre/workspace/redhat/temp-workshop/benchmark-platform/results/calibration/thresholds.json (calibration split), gliner=0.9649, kev=0.5862}

## Quality (quality phase, failures counted as errors)

| Model | n | Intent acc [95% CI] | Intent macro-F1 [CI] | Mean field acc | Extraction EM [CI] | Entity F1 | Halluc. rate | Missing rate | Decision acc | Failures (unsup/malf/timeout/infra) |
|---|---|---|---|---|---|---|---|---|---|---|
| openai-gpt-6-luna | 693 | 88.6% [85.3%–91.2%] | 0.89 [0.87–0.92] | 95.2% | 99.1% [97.6%–99.6%] | 1.00 | 0.2% | 0.2% | 99.4% | 0/0/0/0 |
| openai-gpt-6.1-sol | 693 | 89.0% [85.8%–91.6%] | 0.89 [0.87–0.91] | 94.9% | 100.0% [99.1%–100.0%] | 1.00 | 0.0% | 0.0% | 100.0% | 0/0/0/0 |

## Per-field classification accuracy

| Model | intent | sentiment | category | complaint | all four correct |
|---|---|---|---|---|---|
| openai-gpt-6-luna | 88.6% | 97.3% | 97.5% | 97.3% | 84.8% |
| openai-gpt-6.1-sol | 89.0% | 96.6% | 96.6% | 97.3% | 85.7% |

## Extraction field accuracy

| Model | orderId | items | total | currency | offset acc (n) |
|---|---|---|---|---|---|
| openai-gpt-6-luna | 100.0% | 99.1% | 100.0% | 100.0% | n/a |
| openai-gpt-6.1-sol | 100.0% | 100.0% | 100.0% | 100.0% | n/a |

## Latency (client-side, concurrency 1)

Headline columns: each evaluation input sent once (quality phase), successful requests, full prompt-cache hits excluded. "repeat p50" is the latency-phase median over repeated identical inputs, which a runtime may serve from its prompt cache.

| Model | n | p50 ms [CI] | p90 | p95 | p99 | server p50 ms | cold start ms (load ms) | out tok/s | repeat p50 ms | cache full hits (first-seen / repeat) |
|---|---|---|---|---|---|---|---|---|---|---|
| openai-gpt-6-luna | 693 | 1127.68 [1086.85–1151.64] | 1397.58 | 1509.79 | 1769.09 | n/a | 968.69 (n/a) | n/a | 932.06 | 0 / 0 of 120 |
| openai-gpt-6.1-sol | 693 | 1586.74 [1567.33–1613.24] | 2124.11 | 2574.18 | 3522.96 | n/a | 1215.89 (n/a) | n/a | 1546.14 | 0 / 0 of 120 |

## Throughput

Each concurrency level uses its own window of inputs not used by the warm-up or latency phases.

| Model | concurrency | requests | wall s | req/s | p50 ms | p95 ms | errors | cache full hits |
|---|---|---|---|---|---|---|---|---|
| openai-gpt-6-luna | 1 | 40 | 37.46 | 1.07 | 877.98 | 1123.78 | 0 | 0 |
| openai-gpt-6-luna | 4 | 40 | 11.12 | 3.60 | 955.63 | 1773.93 | 0 | 0 |
| openai-gpt-6-luna | 8 | 40 | 4.87 | 8.22 | 872.17 | 1089.58 | 0 | 0 |
| openai-gpt-6.1-sol | 1 | 40 | 72.02 | 0.56 | 1485.02 | 3281.51 | 0 | 0 |
| openai-gpt-6.1-sol | 4 | 40 | 17.55 | 2.28 | 1454.33 | 3176.89 | 0 | 0 |
| openai-gpt-6.1-sol | 8 | 40 | 9.52 | 4.20 | 1339.15 | 2865.81 | 0 | 0 |

## Confidence calibration (native scores only)

| Model | n | Brier | ECE | risk @0.80 (coverage) | note |
|---|---|---|---|---|---|
| openai-gpt-6-luna | – | – | – | – | provider returns no native label probabilities (none are invented) |
| openai-gpt-6.1-sol | – | – | – | – | provider returns no native label probabilities (none are invented) |

## Abstention on ambiguous inputs

| Model | n ambiguous | predicted `none` | false-positive decisions | overall coverage |
|---|---|---|---|---|
| openai-gpt-6-luna | 64 | 45.3% | 54.7% | 91.0% |
| openai-gpt-6.1-sol | 64 | 37.5% | 62.5% | 92.6% |

## Per language (intent accuracy / extraction exact match)

| Model | en | nl | fr |
|---|---|---|---|
| openai-gpt-6-luna | 90.4% / 100.0% (n=466) | 87.6% / 93.6% (n=111) | 84.4% / 100.0% (n=116) |
| openai-gpt-6.1-sol | 91.2% / 100.0% (n=466) | 85.4% / 100.0% (n=111) | 86.5% / 100.0% (n=116) |

## Input length (long group, extraction exact match / latency p50 ms / unsupported)

| Model | tokens | position | n | EM | p50 ms | unsupported |
|---|---|---|---|---|---|---|
| openai-gpt-6-luna | 100 | end | 7 | 100.0% | 1155.34 | 0 |
| openai-gpt-6-luna | 100 | middle | 6 | 100.0% | 1197.45 | 0 |
| openai-gpt-6-luna | 100 | start | 7 | 100.0% | 1153.95 | 0 |
| openai-gpt-6-luna | 500 | end | 5 | 100.0% | 1172.02 | 0 |
| openai-gpt-6-luna | 500 | middle | 7 | 100.0% | 1175.43 | 0 |
| openai-gpt-6-luna | 500 | start | 7 | 100.0% | 1234.37 | 0 |
| openai-gpt-6-luna | 2000 | end | 5 | 100.0% | 1288.81 | 0 |
| openai-gpt-6-luna | 2000 | middle | 5 | 100.0% | 1223.83 | 0 |
| openai-gpt-6-luna | 2000 | start | 7 | 100.0% | 1112.39 | 0 |
| openai-gpt-6-luna | 5000 | end | 6 | 100.0% | 1171.04 | 0 |
| openai-gpt-6-luna | 5000 | middle | 4 | 100.0% | 1101.39 | 0 |
| openai-gpt-6-luna | 5000 | start | 4 | 100.0% | 1250.09 | 0 |
| openai-gpt-6.1-sol | 100 | end | 7 | 100.0% | 1753.36 | 0 |
| openai-gpt-6.1-sol | 100 | middle | 6 | 100.0% | 1688.04 | 0 |
| openai-gpt-6.1-sol | 100 | start | 7 | 100.0% | 1633.24 | 0 |
| openai-gpt-6.1-sol | 500 | end | 5 | 100.0% | 1586.53 | 0 |
| openai-gpt-6.1-sol | 500 | middle | 7 | 100.0% | 1700.71 | 0 |
| openai-gpt-6.1-sol | 500 | start | 7 | 100.0% | 1586.86 | 0 |
| openai-gpt-6.1-sol | 2000 | end | 5 | 100.0% | 1530.53 | 0 |
| openai-gpt-6.1-sol | 2000 | middle | 5 | 100.0% | 1492.80 | 0 |
| openai-gpt-6.1-sol | 2000 | start | 7 | 100.0% | 1652.64 | 0 |
| openai-gpt-6.1-sol | 5000 | end | 6 | 100.0% | 1551.03 | 0 |
| openai-gpt-6.1-sol | 5000 | middle | 4 | 100.0% | 1561.99 | 0 |
| openai-gpt-6.1-sol | 5000 | start | 4 | 100.0% | 1548.95 | 0 |

## Resources (sampled during this model's phases)

| Model | RSS mean MB | RSS peak MB | CPU mean % | CPU peak % | Ollama loaded size | storage | note |
|---|---|---|---|---|---|---|---|
| openai-gpt-6-luna | n/a | n/a | n/a | n/a | n/a | n/a | not sampled (pipeline or remote endpoint; see stage models) |
| openai-gpt-6.1-sol | n/a | n/a | n/a | n/a | n/a | n/a | not sampled (pipeline or remote endpoint; see stage models) |

GPU utilization and energy: not measured (no reliable unprivileged method on this platform).

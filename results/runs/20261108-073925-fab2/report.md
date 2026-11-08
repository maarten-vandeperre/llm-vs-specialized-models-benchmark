# Benchmark report 20261108-073925-fab2

> **Synthetic benchmark.** Synthetic, fictional coffee-shop dataset. Results do not generalize to production customers.

- Status: COMPLETED, created 2026-11-08T07:39:25.013525Z, finished 2026-11-08T08:22:18.567325Z
- Dataset: `coffee-v1` split `evaluation`, groups all, limit null, records selected 693, sha256 `627e6d79f1b9c54031c6f9c421686e108c133d2327338c87956cc99db631d49d`
- Method: warm-up 10, repetitions 5, latency sample 30, concurrency [1, 4, 8], throughput requests 40, cold start true, seed 42, timeout 180 s
- Model order (seeded shuffle): [claude-haiku-5.5, claude-opus-5.5]
- Hardware: Apple M1 Max, 64.00 GiB memory, 10 CPUs, Mac OS X 26.6.2
- Software: Java 25.0.2, Quarkus 3.40.1.1, Ollama 0.40.15, GLiNER service {python=3.12.14, gliner2=2.1.0, torch=2.15.0, transformers=5.22.2}
- Hybrid thresholds: {kev=0.5862, gliner=0.9649, source=/Users/maartenvandeperre/workspace/redhat/temp-workshop/benchmark-platform/results/calibration/thresholds.json (calibration split)}

## Quality (quality phase, failures counted as errors)

| Model | n | Intent acc [95% CI] | Intent macro-F1 [CI] | Mean field acc | Extraction EM [CI] | Entity F1 | Halluc. rate | Missing rate | Decision acc | Failures (unsup/malf/timeout/infra) |
|---|---|---|---|---|---|---|---|---|---|---|
| claude-haiku-5.5 | 693 | 85.0% [81.4%–88.0%] | 0.86 [0.84–0.89] | 90.4% | 99.3% [97.9%–99.8%] | 1.00 | 0.0% | 0.7% | 94.9% | 0/0/0/0 |
| claude-opus-5.5 | 693 | 91.9% [89.0%–94.1%] | 0.93 [0.90–0.95] | 96.9% | 100.0% [99.1%–100.0%] | 1.00 | 0.0% | 0.0% | 98.9% | 0/0/0/0 |

## Per-field classification accuracy

| Model | intent | sentiment | category | complaint | all four correct |
|---|---|---|---|---|---|
| claude-haiku-5.5 | 85.0% | 94.0% | 88.8% | 93.7% | 76.5% |
| claude-opus-5.5 | 91.9% | 97.8% | 99.1% | 98.9% | 89.5% |

## Extraction field accuracy

| Model | orderId | items | total | currency | offset acc (n) |
|---|---|---|---|---|---|
| claude-haiku-5.5 | 99.3% | 99.3% | 99.3% | 99.3% | n/a |
| claude-opus-5.5 | 100.0% | 100.0% | 100.0% | 100.0% | n/a |

## Latency (client-side, concurrency 1)

Headline columns: each evaluation input sent once (quality phase), successful requests, full prompt-cache hits excluded. "repeat p50" is the latency-phase median over repeated identical inputs, which a runtime may serve from its prompt cache.

| Model | n | p50 ms [CI] | p90 | p95 | p99 | server p50 ms | cold start ms (load ms) | out tok/s | repeat p50 ms | cache full hits (first-seen / repeat) |
|---|---|---|---|---|---|---|---|---|---|---|
| claude-haiku-5.5 | 693 | 1001.62 [983.90–1016.94] | 1358.99 | 1565.98 | 2193.12 | n/a | 914.83 (n/a) | n/a | 873.69 | 0 / 0 of 120 |
| claude-opus-5.5 | 693 | 1658.53 [1637.95–1681.90] | 2346.86 | 3494.86 | 5061.93 | n/a | 1387.50 (n/a) | n/a | 1545.55 | 0 / 0 of 120 |

## Throughput

Each concurrency level uses its own window of inputs not used by the warm-up or latency phases.

| Model | concurrency | requests | wall s | req/s | p50 ms | p95 ms | errors | cache full hits |
|---|---|---|---|---|---|---|---|---|
| claude-haiku-5.5 | 1 | 40 | 38.43 | 1.04 | 834.06 | 1122.56 | 0 | 0 |
| claude-haiku-5.5 | 4 | 40 | 9.19 | 4.35 | 857.58 | 1289.49 | 0 | 0 |
| claude-haiku-5.5 | 8 | 40 | 5.27 | 7.59 | 927.07 | 1360.25 | 0 | 0 |
| claude-opus-5.5 | 1 | 40 | 93.49 | 0.43 | 1712.74 | 4992.57 | 0 | 0 |
| claude-opus-5.5 | 4 | 40 | 19.45 | 2.06 | 1668.58 | 3449.03 | 0 | 0 |
| claude-opus-5.5 | 8 | 40 | 10.85 | 3.69 | 1623.80 | 4603.44 | 0 | 0 |

## Confidence calibration (native scores only)

| Model | n | Brier | ECE | risk @0.80 (coverage) | note |
|---|---|---|---|---|---|
| claude-haiku-5.5 | – | – | – | – | provider returns no native label probabilities (none are invented) |
| claude-opus-5.5 | – | – | – | – | provider returns no native label probabilities (none are invented) |

## Abstention on ambiguous inputs

| Model | n ambiguous | predicted `none` | false-positive decisions | overall coverage |
|---|---|---|---|---|
| claude-haiku-5.5 | 64 | 48.4% | 51.6% | 87.7% |
| claude-opus-5.5 | 64 | 65.6% | 34.4% | 88.3% |

## Per language (intent accuracy / extraction exact match)

| Model | en | nl | fr |
|---|---|---|---|
| claude-haiku-5.5 | 86.2% / 99.0% (n=466) | 85.4% / 100.0% (n=111) | 81.3% / 100.0% (n=116) |
| claude-opus-5.5 | 92.3% / 100.0% (n=466) | 92.1% / 100.0% (n=111) | 90.6% / 100.0% (n=116) |

## Input length (long group, extraction exact match / latency p50 ms / unsupported)

| Model | tokens | position | n | EM | p50 ms | unsupported |
|---|---|---|---|---|---|---|
| claude-haiku-5.5 | 100 | end | 7 | 100.0% | 996.95 | 0 |
| claude-haiku-5.5 | 100 | middle | 6 | 100.0% | 973.03 | 0 |
| claude-haiku-5.5 | 100 | start | 7 | 100.0% | 1127.98 | 0 |
| claude-haiku-5.5 | 500 | end | 5 | 100.0% | 1535.37 | 0 |
| claude-haiku-5.5 | 500 | middle | 7 | 100.0% | 1237.42 | 0 |
| claude-haiku-5.5 | 500 | start | 7 | 100.0% | 1201.68 | 0 |
| claude-haiku-5.5 | 2000 | end | 5 | 100.0% | 1231.73 | 0 |
| claude-haiku-5.5 | 2000 | middle | 5 | 60.0% | 1173.39 | 0 |
| claude-haiku-5.5 | 2000 | start | 7 | 100.0% | 1241.14 | 0 |
| claude-haiku-5.5 | 5000 | end | 6 | 100.0% | 1449.56 | 0 |
| claude-haiku-5.5 | 5000 | middle | 4 | 75.0% | 1185.08 | 0 |
| claude-haiku-5.5 | 5000 | start | 4 | 100.0% | 1622.74 | 0 |
| claude-opus-5.5 | 100 | end | 7 | 100.0% | 1395.01 | 0 |
| claude-opus-5.5 | 100 | middle | 6 | 100.0% | 1377.51 | 0 |
| claude-opus-5.5 | 100 | start | 7 | 100.0% | 1873.12 | 0 |
| claude-opus-5.5 | 500 | end | 5 | 100.0% | 1987.71 | 0 |
| claude-opus-5.5 | 500 | middle | 7 | 100.0% | 1732.91 | 0 |
| claude-opus-5.5 | 500 | start | 7 | 100.0% | 1524.41 | 0 |
| claude-opus-5.5 | 2000 | end | 5 | 100.0% | 2065.93 | 0 |
| claude-opus-5.5 | 2000 | middle | 5 | 100.0% | 1936.16 | 0 |
| claude-opus-5.5 | 2000 | start | 7 | 100.0% | 2396.28 | 0 |
| claude-opus-5.5 | 5000 | end | 6 | 100.0% | 2767.99 | 0 |
| claude-opus-5.5 | 5000 | middle | 4 | 100.0% | 2313.20 | 0 |
| claude-opus-5.5 | 5000 | start | 4 | 100.0% | 1758.84 | 0 |

## Resources (sampled during this model's phases)

| Model | RSS mean MB | RSS peak MB | CPU mean % | CPU peak % | Ollama loaded size | storage | note |
|---|---|---|---|---|---|---|---|
| claude-haiku-5.5 | n/a | n/a | n/a | n/a | n/a | n/a | not sampled (pipeline or remote endpoint; see stage models) |
| claude-opus-5.5 | n/a | n/a | n/a | n/a | n/a | n/a | not sampled (pipeline or remote endpoint; see stage models) |

GPU utilization and energy: not measured (no reliable unprivileged method on this platform).

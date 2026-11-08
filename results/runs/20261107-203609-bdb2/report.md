# Benchmark report 20261107-203609-bdb2

> **Synthetic benchmark.** Synthetic, fictional coffee-shop dataset. Results do not generalize to production customers.

- Status: COMPLETED, created 2026-11-07T20:36:09.433030744Z, finished 2026-11-07T20:45:54.762083476Z
- Dataset: `coffee-v1` split `evaluation`, groups all, limit null, records selected 693, sha256 `627e6d79f1b9c54031c6f9c421686e108c133d2327338c87956cc99db631d49d`
- Method: warm-up 10, repetitions 5, latency sample 30, concurrency [1, 4, 8], throughput requests 40, cold start true, seed 42, timeout 300 s
- Model order (seeded shuffle): [gliner2.5-base]
- Hardware: Intel(R) Xeon(R) Platinum 8259CL CPU @ 2.50GHz, n/a memory, 1 CPUs, Linux 5.14.0-570.135.1.el9_6.x86_64
- Software: Java 25.0.5, Quarkus 3.40.1.1, Ollama unavailable, GLiNER service {python=3.12.15, gliner2=2.1.0, torch=2.15.0+cpu, transformers=5.22.2}
- Hybrid thresholds: {kev=0.8, gliner=0.8, source=configuration defaults}

## Quality (quality phase, failures counted as errors)

| Model | n | Intent acc [95% CI] | Intent macro-F1 [CI] | Mean field acc | Extraction EM [CI] | Entity F1 | Halluc. rate | Missing rate | Decision acc | Failures (unsup/malf/timeout/infra) |
|---|---|---|---|---|---|---|---|---|---|---|
| gliner2.5-base | 693 | 62.8% [58.2%–67.1%] | 0.68 [0.65–0.72] | 66.6% | 75.0% [70.7%–78.9%] | 0.93 | 0.0% | 7.1% | 71.2% | 0/0/0/14 |

## Per-field classification accuracy

| Model | intent | sentiment | category | complaint | all four correct |
|---|---|---|---|---|---|
| gliner2.5-base | 62.8% | 60.8% | 71.8% | 71.1% | 27.4% |

## Extraction field accuracy

| Model | orderId | items | total | currency | offset acc (n) |
|---|---|---|---|---|---|
| gliner2.5-base | 95.8% | 75.9% | 96.5% | 96.5% | 99.2% (373) |

## Latency (client-side, successful requests, concurrency 1)

| Model | n | p50 ms [CI] | p90 | p95 | p99 | server p50 ms | cold start ms (load ms) | out tok/s |
|---|---|---|---|---|---|---|---|---|
| gliner2.5-base | 679 | 729.32 [722.86–737.59] | 1009.27 | 1072.48 | 8025.04 | 725.88 | 752.42 (n/a) | n/a |

## Throughput

| Model | concurrency | requests | wall s | req/s | p50 ms | p95 ms | errors |
|---|---|---|---|---|---|---|---|
| gliner2.5-base | 1 | 40 | 0.10 | 0.00 | n/a | n/a | 40 |
| gliner2.5-base | 4 | 40 | 0.02 | 0.00 | n/a | n/a | 40 |
| gliner2.5-base | 8 | 40 | 0.08 | 0.00 | n/a | n/a | 40 |

## Confidence calibration (native scores only)

| Model | n | Brier | ECE | risk @0.80 (coverage) | note |
|---|---|---|---|---|---|
| gliner2.5-base | 1784 | 0.23 | 0.19 | 24.1% (69.7%) | top-label score |

## Abstention on ambiguous inputs

| Model | n ambiguous | predicted `none` | false-positive decisions | overall coverage |
|---|---|---|---|---|
| gliner2.5-base | 64 | 76.6% | 23.4% | 65.0% |

## Per language (intent accuracy / extraction exact match)

| Model | en | nl | fr |
|---|---|---|---|
| gliner2.5-base | 68.2% / 75.4% (n=466) | 46.1% / 72.6% (n=111) | 63.5% / 75.4% (n=116) |

## Input length (long group, extraction exact match / latency p50 ms / unsupported)

| Model | tokens | position | n | EM | p50 ms | unsupported |
|---|---|---|---|---|---|---|
| gliner2.5-base | 100 | end | 7 | 71.4% | 304.36 | 0 |
| gliner2.5-base | 100 | middle | 6 | 33.3% | 304.61 | 0 |
| gliner2.5-base | 100 | start | 7 | 85.7% | 337.34 | 0 |
| gliner2.5-base | 500 | end | 5 | 60.0% | 883.63 | 0 |
| gliner2.5-base | 500 | middle | 7 | 0.0% | 893.14 | 0 |
| gliner2.5-base | 500 | start | 7 | 42.9% | 932.21 | 0 |
| gliner2.5-base | 2000 | end | 5 | 20.0% | 8061.97 | 0 |
| gliner2.5-base | 2000 | middle | 5 | 60.0% | 7852.95 | 0 |
| gliner2.5-base | 2000 | start | 7 | 57.1% | 8144.02 | 0 |
| gliner2.5-base | 5000 | end | 6 | 0.0% | n/a | 0 |
| gliner2.5-base | 5000 | middle | 4 | 0.0% | n/a | 0 |
| gliner2.5-base | 5000 | start | 4 | 0.0% | n/a | 0 |

## Resources (sampled during this model's phases)

| Model | RSS mean MB | RSS peak MB | CPU mean % | CPU peak % | Ollama loaded size | storage | note |
|---|---|---|---|---|---|---|---|
| gliner2.5-base | n/a | n/a | n/a | n/a | n/a | n/a | ps unavailable: Cannot run program "ps": Exec failed, error: 2 (No such file or directory)  |

GPU utilization and energy: not measured (no reliable unprivileged method on this platform).

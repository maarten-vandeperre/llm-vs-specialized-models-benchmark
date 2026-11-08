# Benchmark report 20261107-220443-d679

> **Synthetic benchmark.** Synthetic, fictional coffee-shop dataset. Results do not generalize to production customers.

- Status: COMPLETED, created 2026-11-07T22:04:43.583866474Z, finished 2026-11-07T22:36:14.069335604Z
- Dataset: `coffee-v1` split `evaluation`, groups all, limit null, records selected 693, sha256 `627e6d79f1b9c54031c6f9c421686e108c133d2327338c87956cc99db631d49d`
- Method: warm-up 10, repetitions 5, latency sample 30, concurrency [1, 4, 8], throughput requests 40, cold start true, seed 42, timeout 300 s
- Model order (seeded shuffle): [gliner2.5-base]
- Hardware: Intel(R) Xeon(R) Platinum 8175M CPU @ 2.50GHz, n/a memory, 1 CPUs, Linux 5.14.0-570.135.1.el9_6.x86_64
- Software: Java 25.0.5, Quarkus 3.40.1.1, Ollama unavailable, GLiNER service {python=3.12.15, gliner2=2.1.0, torch=2.15.0+cpu, transformers=5.22.2}
- Hybrid thresholds: {kev=0.8, gliner=0.8, source=configuration defaults}

## Quality (quality phase, failures counted as errors)

| Model | n | Intent acc [95% CI] | Intent macro-F1 [CI] | Mean field acc | Extraction EM [CI] | Entity F1 | Halluc. rate | Missing rate | Decision acc | Failures (unsup/malf/timeout/infra) |
|---|---|---|---|---|---|---|---|---|---|---|
| gliner2.5-base | 693 | 62.8% [58.2%–67.1%] | 0.68 [0.65–0.72] | 66.6% | 76.6% [72.4%–80.4%] | 0.94 | 0.0% | 4.4% | 71.2% | 0/0/0/0 |

## Per-field classification accuracy

| Model | intent | sentiment | category | complaint | all four correct |
|---|---|---|---|---|---|
| gliner2.5-base | 62.8% | 60.8% | 71.8% | 71.1% | 27.4% |

## Extraction field accuracy

| Model | orderId | items | total | currency | offset acc (n) |
|---|---|---|---|---|---|
| gliner2.5-base | 99.1% | 77.6% | 99.8% | 99.8% | 99.2% (387) |

## Latency (client-side, concurrency 1)

Headline columns: each evaluation input sent once (quality phase), successful requests, full prompt-cache hits excluded. "repeat p50" is the latency-phase median over repeated identical inputs, which a runtime may serve from its prompt cache.

| Model | n | p50 ms [CI] | p90 | p95 | p99 | server p50 ms | cold start ms (load ms) | out tok/s | repeat p50 ms | cache full hits (first-seen / repeat) |
|---|---|---|---|---|---|---|---|---|---|---|
| gliner2.5-base | 693 | 776.14 [760.82–796.15] | 1048.76 | 1209.12 | 55924.26 | 772.70 | 1009.99 (n/a) | n/a | 746.27 | 0 / 0 of 120 |

## Throughput

Each concurrency level uses its own window of inputs not used by the warm-up or latency phases.

| Model | concurrency | requests | wall s | req/s | p50 ms | p95 ms | errors | cache full hits |
|---|---|---|---|---|---|---|---|---|
| gliner2.5-base | 1 | 40 | 29.96 | 1.34 | 748.21 | 800.67 | 0 | 0 |
| gliner2.5-base | 4 | 40 | 31.71 | 1.26 | 3154.24 | 3388.07 | 0 | 0 |
| gliner2.5-base | 8 | 40 | 34.14 | 1.17 | 6785.83 | 7150.94 | 0 | 0 |

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
| gliner2.5-base | 68.2% / 77.8% (n=466) | 46.1% / 72.6% (n=111) | 63.5% / 75.4% (n=116) |

## Input length (long group, extraction exact match / latency p50 ms / unsupported)

| Model | tokens | position | n | EM | p50 ms | unsupported |
|---|---|---|---|---|---|---|
| gliner2.5-base | 100 | end | 7 | 71.4% | 314.81 | 0 |
| gliner2.5-base | 100 | middle | 6 | 33.3% | 308.80 | 0 |
| gliner2.5-base | 100 | start | 7 | 85.7% | 317.30 | 0 |
| gliner2.5-base | 500 | end | 5 | 60.0% | 924.87 | 0 |
| gliner2.5-base | 500 | middle | 7 | 0.0% | 966.68 | 0 |
| gliner2.5-base | 500 | start | 7 | 42.9% | 963.16 | 0 |
| gliner2.5-base | 2000 | end | 5 | 20.0% | 6819.42 | 0 |
| gliner2.5-base | 2000 | middle | 5 | 60.0% | 6836.93 | 0 |
| gliner2.5-base | 2000 | start | 7 | 57.1% | 6847.69 | 0 |
| gliner2.5-base | 5000 | end | 6 | 66.7% | 56141.34 | 0 |
| gliner2.5-base | 5000 | middle | 4 | 25.0% | 56269.02 | 0 |
| gliner2.5-base | 5000 | start | 4 | 50.0% | 32985.66 | 0 |

## Resources (sampled during this model's phases)

| Model | RSS mean MB | RSS peak MB | CPU mean % | CPU peak % | Ollama loaded size | storage | note |
|---|---|---|---|---|---|---|---|
| gliner2.5-base | n/a | n/a | n/a | n/a | n/a | 0.73 GiB | ps unavailable: Cannot run program "ps": Exec failed, error: 2 (No such file or directory)  |

GPU utilization and energy: not measured (no reliable unprivileged method on this platform).

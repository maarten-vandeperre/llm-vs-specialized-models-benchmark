# Benchmark report 20261107-215748-1a28

> **Synthetic benchmark.** Synthetic, fictional coffee-shop dataset. Results do not generalize to production customers.

- Status: COMPLETED, created 2026-11-07T21:57:48.730797Z, finished 2026-11-07T23:32:07.013480Z
- Dataset: `coffee-v1` split `evaluation`, groups all, limit null, records selected 693, sha256 `627e6d79f1b9c54031c6f9c421686e108c133d2327338c87956cc99db631d49d`
- Method: warm-up 10, repetitions 5, latency sample 30, concurrency [1, 4, 8], throughput requests 40, cold start true, seed 42, timeout 180 s
- Model order (seeded shuffle): [hybrid-gliner-kev-qwen, gliner2.5-multi, nuextract3, gliner2.5-base, hybrid-gliner-qwen, kev-0.8b-candsel, gliner2.5-decide, hybrid-gliner-kev, kev-0.8b, gliner2.5-small, qwen3.5-4b]
- Hardware: Apple M1 Max, 64.00 GiB memory, 10 CPUs, Mac OS X 26.6.2
- Software: Java 25.0.2, Quarkus 3.40.1.1, Ollama 0.40.15, GLiNER service {python=3.12.14, gliner2=2.1.0, torch=2.15.0, transformers=5.22.2}
- Hybrid thresholds: {kev=0.5862, gliner=0.9649, source=/Users/maartenvandeperre/workspace/redhat/temp-workshop/benchmark-platform/results/calibration/thresholds.json (calibration split)}

## Quality (quality phase, failures counted as errors)

| Model | n | Intent acc [95% CI] | Intent macro-F1 [CI] | Mean field acc | Extraction EM [CI] | Entity F1 | Halluc. rate | Missing rate | Decision acc | Failures (unsup/malf/timeout/infra) |
|---|---|---|---|---|---|---|---|---|---|---|
| hybrid-gliner-kev-qwen | 693 | 81.8% [78.0%–85.1%] | 0.82 [0.79–0.85] | 87.5% | 78.1% [73.9%–81.7%] | 0.95 | 0.0% | 4.3% | 96.6% | 0/0/0/0 |
| gliner2.5-multi | 693 | 66.8% [62.3%–71.0%] | 0.65 [0.62–0.68] | 44.1% | 60.1% [55.4%–64.7%] | 0.82 | 0.0% | 7.8% | 78.0% | 0/0/0/0 |
| nuextract3 | 693 | 79.1% [75.1%–82.7%] | 0.78 [0.74–0.80] | 80.3% | 99.8% [98.7%–100.0%] | 1.00 | 0.0% | 0.0% | 93.8% | 0/0/0/0 |
| gliner2.5-base | 693 | 62.8% [58.2%–67.1%] | 0.68 [0.65–0.72] | 66.6% | 76.6% [72.4%–80.4%] | 0.94 | 0.0% | 4.4% | 71.2% | 0/0/0/0 |
| hybrid-gliner-qwen | 693 | 81.2% [77.3%–84.5%] | 0.81 [0.78–0.84] | 86.3% | 80.9% [76.9%–84.4%] | 0.95 | 0.1% | 4.2% | 96.6% | 0/0/0/0 |
| kev-0.8b-candsel | 693 | 69.3% [64.9%–73.4%] | 0.75 [0.72–0.78] | 79.3% | 20.8% [17.2%–24.9%] | 0.73 | 24.3% | 8.2% | 77.4% | 0/0/0/0 |
| gliner2.5-decide | 446 | 75.8% [71.6%–79.5%] | 0.75 [0.72–0.77] | 66.6% | – | – | – | – | – | 0/0/0/0 |
| hybrid-gliner-kev | 693 | 70.0% [65.5%–74.0%] | 0.75 [0.72–0.78] | 79.4% | 78.1% [73.9%–81.7%] | 0.95 | 0.0% | 4.3% | 80.8% | 0/0/0/0 |
| kev-0.8b | 446 | 70.0% [65.5%–74.0%] | 0.75 [0.72–0.78] | 79.4% | – | – | – | – | – | 0/0/0/0 |
| gliner2.5-small | 693 | 60.8% [56.2%–65.2%] | 0.59 [0.55–0.63] | 43.4% | 62.3% [57.6%–66.8%] | 0.89 | 0.6% | 2.9% | 67.2% | 0/0/0/0 |
| qwen3.5-4b | 693 | 81.4% [77.5%–84.7%] | 0.81 [0.78–0.84] | 88.6% | 91.0% [87.9%–93.4%] | 0.96 | 0.2% | 6.8% | 96.1% | 0/0/0/0 |

## Per-field classification accuracy

| Model | intent | sentiment | category | complaint | all four correct |
|---|---|---|---|---|---|
| hybrid-gliner-kev-qwen | 81.8% | 91.5% | 82.3% | 94.2% | 60.5% |
| gliner2.5-multi | 66.8% | 41.3% | 24.7% | 43.7% | 4.0% |
| nuextract3 | 79.1% | 92.8% | 65.7% | 83.4% | 55.4% |
| gliner2.5-base | 62.8% | 60.8% | 71.8% | 71.1% | 27.4% |
| hybrid-gliner-qwen | 81.2% | 87.7% | 82.3% | 94.2% | 58.5% |
| kev-0.8b-candsel | 69.3% | 86.6% | 74.0% | 87.4% | 37.4% |
| gliner2.5-decide | 75.8% | 66.1% | 56.5% | 67.9% | 16.6% |
| hybrid-gliner-kev | 70.0% | 86.3% | 74.2% | 87.2% | 38.3% |
| kev-0.8b | 70.0% | 86.3% | 74.2% | 87.2% | 38.3% |
| gliner2.5-small | 60.8% | 39.5% | 28.0% | 45.3% | 3.6% |
| qwen3.5-4b | 81.4% | 94.4% | 84.1% | 94.4% | 64.6% |

## Extraction field accuracy

| Model | orderId | items | total | currency | offset acc (n) |
|---|---|---|---|---|---|
| hybrid-gliner-kev-qwen | 99.8% | 78.3% | 99.8% | 99.8% | 100.0% (387) |
| gliner2.5-multi | 100.0% | 60.1% | 100.0% | 100.0% | 100.0% (387) |
| nuextract3 | 99.8% | 100.0% | 100.0% | 100.0% | n/a |
| gliner2.5-base | 99.1% | 77.6% | 99.8% | 99.8% | 99.2% (387) |
| hybrid-gliner-qwen | 99.3% | 81.1% | 99.8% | 99.8% | 100.0% (272) |
| kev-0.8b-candsel | 99.3% | 23.4% | 84.7% | 88.7% | n/a |
| hybrid-gliner-kev | 99.8% | 78.3% | 99.8% | 99.8% | 100.0% (387) |
| gliner2.5-small | 95.3% | 66.3% | 96.5% | 97.9% | 96.9% (384) |
| qwen3.5-4b | 93.6% | 91.0% | 94.1% | 94.1% | n/a |

## Latency (client-side, concurrency 1)

Headline columns: each evaluation input sent once (quality phase), successful requests, full prompt-cache hits excluded. "repeat p50" is the latency-phase median over repeated identical inputs, which a runtime may serve from its prompt cache.

| Model | n | p50 ms [CI] | p90 | p95 | p99 | server p50 ms | cold start ms (load ms) | out tok/s | repeat p50 ms | cache full hits (first-seen / repeat) |
|---|---|---|---|---|---|---|---|---|---|---|
| hybrid-gliner-kev-qwen | 668 | 815.17 [803.17–826.24] | 1039.63 | 1065.70 | 4711.28 | n/a | 1414.06 (n/a) | n/a | 589.07 | 25 / 90 of 120 |
| gliner2.5-multi | 693 | 192.53 [190.93–193.50] | 249.84 | 291.43 | 2474.40 | 191.15 | 233.14 (n/a) | n/a | 192.80 | 0 / 0 of 120 |
| nuextract3 | 683 | 1358.68 [1321.31–1411.38] | 1815.71 | 1987.50 | 3297.43 | 1357.54 | 6303.31 (4846.64) | 71.91 | 1254.76 | 10 / 0 of 120 |
| gliner2.5-base | 693 | 160.67 [159.70–161.65] | 198.20 | 205.52 | 4752.30 | 159.41 | 199.41 (n/a) | n/a | 158.68 | 0 / 0 of 120 |
| hybrid-gliner-qwen | 667 | 807.17 [785.00–816.96] | 2276.19 | 2520.06 | 5332.15 | n/a | 2410.27 (n/a) | n/a | 928.87 | 26 / 66 of 120 |
| kev-0.8b-candsel | 668 | 544.13 [536.19–547.55] | 703.86 | 713.66 | 1440.14 | n/a | 702.98 (n/a) | n/a | 4.52 | 25 / 90 of 120 |
| gliner2.5-decide | 446 | 467.02 [465.01–468.73] | 500.73 | 514.82 | 544.21 | 465.81 | 501.55 (n/a) | n/a | 460.90 | 0 / 0 of 120 |
| hybrid-gliner-kev | 668 | 180.19 [179.47–180.92] | 301.16 | 313.61 | 4604.74 | n/a | 704.02 (n/a) | n/a | 4.48 | 25 / 90 of 120 |
| kev-0.8b | 421 | 182.07 [181.57–182.40] | 189.96 | 191.16 | 194.17 | n/a | 722.26 (n/a) | n/a | 4.16 | 25 / 90 of 120 |
| gliner2.5-small | 693 | 85.21 [84.85–85.90] | 106.04 | 112.91 | 2455.04 | 84.21 | 123.38 (n/a) | n/a | 84.09 | 0 / 0 of 120 |
| qwen3.5-4b | 667 | 1398.23 [1236.86–1527.53] | 2049.69 | 2112.73 | 5526.28 | 1397.10 | 1742.00 (710.50) | 69.02 | 595.73 | 26 / 90 of 120 |

## Throughput

Each concurrency level uses its own window of inputs not used by the warm-up or latency phases.

| Model | concurrency | requests | wall s | req/s | p50 ms | p95 ms | errors | cache full hits |
|---|---|---|---|---|---|---|---|---|
| hybrid-gliner-kev-qwen | 1 | 40 | 30.00 | 1.33 | 820.19 | 871.27 | 0 | 1 |
| hybrid-gliner-kev-qwen | 4 | 40 | 26.04 | 1.54 | 2920.62 | 3201.30 | 0 | 0 |
| hybrid-gliner-kev-qwen | 8 | 40 | 22.93 | 1.74 | 5256.02 | 6044.22 | 0 | 2 |
| gliner2.5-multi | 1 | 40 | 7.71 | 5.19 | 190.88 | 201.78 | 0 | 0 |
| gliner2.5-multi | 4 | 40 | 7.75 | 5.16 | 773.72 | 787.65 | 0 | 0 |
| gliner2.5-multi | 8 | 40 | 7.70 | 5.20 | 1532.81 | 1557.87 | 0 | 0 |
| nuextract3 | 1 | 40 | 49.70 | 0.80 | 1263.66 | 1477.01 | 0 | 0 |
| nuextract3 | 4 | 40 | 49.48 | 0.81 | 4907.71 | 5425.20 | 0 | 0 |
| nuextract3 | 8 | 40 | 49.64 | 0.81 | 9982.06 | 10317.67 | 0 | 0 |
| gliner2.5-base | 1 | 40 | 6.47 | 6.19 | 159.34 | 170.95 | 0 | 0 |
| gliner2.5-base | 4 | 40 | 6.40 | 6.25 | 637.96 | 657.58 | 0 | 0 |
| gliner2.5-base | 8 | 40 | 6.34 | 6.31 | 1262.91 | 1278.56 | 0 | 0 |
| hybrid-gliner-qwen | 1 | 40 | 29.33 | 1.36 | 824.85 | 1009.03 | 0 | 1 |
| hybrid-gliner-qwen | 4 | 40 | 21.32 | 1.88 | 2583.27 | 2694.70 | 0 | 1 |
| hybrid-gliner-qwen | 8 | 40 | 21.61 | 1.85 | 4981.51 | 5312.79 | 0 | 2 |
| kev-0.8b-candsel | 1 | 40 | 7.01 | 5.71 | 177.89 | 185.19 | 0 | 1 |
| kev-0.8b-candsel | 4 | 40 | 7.18 | 5.57 | 712.95 | 738.63 | 0 | 0 |
| kev-0.8b-candsel | 8 | 40 | 6.79 | 5.89 | 1421.74 | 1432.16 | 0 | 2 |
| gliner2.5-decide | 1 | 40 | 18.51 | 2.16 | 457.50 | 496.35 | 0 | 0 |
| gliner2.5-decide | 4 | 40 | 18.37 | 2.18 | 1830.13 | 1892.55 | 0 | 0 |
| gliner2.5-decide | 8 | 40 | 18.43 | 2.17 | 3681.15 | 3732.65 | 0 | 0 |
| hybrid-gliner-kev | 1 | 40 | 7.13 | 5.61 | 181.13 | 188.50 | 0 | 1 |
| hybrid-gliner-kev | 4 | 40 | 7.18 | 5.57 | 716.27 | 722.67 | 0 | 0 |
| hybrid-gliner-kev | 8 | 40 | 6.82 | 5.87 | 1430.55 | 1438.57 | 0 | 2 |
| kev-0.8b | 1 | 40 | 7.12 | 5.62 | 181.42 | 185.47 | 0 | 1 |
| kev-0.8b | 4 | 40 | 7.20 | 5.55 | 720.41 | 724.01 | 0 | 0 |
| kev-0.8b | 8 | 40 | 6.84 | 5.85 | 1432.35 | 1441.32 | 0 | 2 |
| gliner2.5-small | 1 | 40 | 3.39 | 11.79 | 84.22 | 89.44 | 0 | 0 |
| gliner2.5-small | 4 | 40 | 3.36 | 11.92 | 335.56 | 344.37 | 0 | 0 |
| gliner2.5-small | 8 | 40 | 3.35 | 11.93 | 669.77 | 675.80 | 0 | 0 |
| qwen3.5-4b | 1 | 40 | 26.23 | 1.53 | 646.26 | 695.35 | 0 | 1 |
| qwen3.5-4b | 4 | 40 | 26.18 | 1.53 | 2607.98 | 2706.63 | 0 | 1 |
| qwen3.5-4b | 8 | 40 | 25.95 | 1.54 | 5169.69 | 5315.63 | 0 | 2 |

## Confidence calibration (native scores only)

| Model | n | Brier | ECE | risk @0.80 (coverage) | note |
|---|---|---|---|---|---|
| hybrid-gliner-kev-qwen | 244 | 0.11 | 0.21 | 0.0% (29.5%) | top-label score |
| gliner2.5-multi | 1784 | 0.30 | 0.26 | 37.5% (34.7%) | top-label score |
| nuextract3 | – | – | – | – | provider returns no native label probabilities (none are invented) |
| gliner2.5-base | 1784 | 0.23 | 0.19 | 24.1% (69.7%) | top-label score |
| hybrid-gliner-qwen | 348 | 0.11 | 0.08 | 10.5% (87.4%) | top-label score |
| kev-0.8b-candsel | 1784 | 0.17 | 0.20 | 3.2% (16.0%) | top-label score |
| gliner2.5-decide | 1784 | 0.21 | 0.19 | 0.0% (8.9%) | top-label score |
| hybrid-gliner-kev | 1784 | 0.17 | 0.20 | 3.2% (16.0%) | top-label score |
| kev-0.8b | 1784 | 0.17 | 0.20 | 3.2% (16.0%) | top-label score |
| gliner2.5-small | 1784 | 0.27 | 0.18 | 39.4% (26.2%) | top-label score |
| qwen3.5-4b | – | – | – | – | provider returns no native label probabilities (none are invented) |

## Abstention on ambiguous inputs

| Model | n ambiguous | predicted `none` | false-positive decisions | overall coverage |
|---|---|---|---|---|
| hybrid-gliner-kev-qwen | 64 | 25.0% | 75.0% | 95.7% |
| gliner2.5-multi | 64 | 1.6% | 98.4% | 98.2% |
| nuextract3 | 64 | 59.4% | 40.6% | 81.8% |
| gliner2.5-base | 64 | 76.6% | 23.4% | 65.0% |
| hybrid-gliner-qwen | 64 | 25.0% | 75.0% | 94.8% |
| kev-0.8b-candsel | 64 | 20.3% | 79.7% | 84.3% |
| gliner2.5-decide | 64 | 0.0% | 100.0% | 99.6% |
| hybrid-gliner-kev | 64 | 21.9% | 78.1% | 84.5% |
| kev-0.8b | 64 | 21.9% | 78.1% | 84.5% |
| gliner2.5-small | 64 | 6.3% | 93.8% | 96.6% |
| qwen3.5-4b | 64 | 26.6% | 73.4% | 94.8% |

## Per language (intent accuracy / extraction exact match)

| Model | en | nl | fr |
|---|---|---|---|
| hybrid-gliner-kev-qwen | 82.8% / 78.2% (n=466) | 83.2% / 77.4% (n=111) | 78.1% / 78.3% (n=116) |
| gliner2.5-multi | 68.6% / 57.7% (n=466) | 62.9% / 79.0% (n=111) | 65.6% / 53.6% (n=116) |
| nuextract3 | 78.2% / 100.0% (n=466) | 82.0% / 100.0% (n=111) | 79.2% / 98.6% (n=116) |
| gliner2.5-base | 68.2% / 77.8% (n=466) | 46.1% / 72.6% (n=111) | 63.5% / 75.4% (n=116) |
| hybrid-gliner-qwen | 82.4% / 78.8% (n=466) | 82.0% / 85.5% (n=111) | 77.1% / 85.5% (n=116) |
| kev-0.8b-candsel | 73.2% / 25.6% (n=466) | 61.8% / 14.5% (n=111) | 65.6% / 5.8% (n=116) |
| gliner2.5-decide | 78.5% / n/a (n=261) | 69.7% / n/a (n=89) | 74.0% / n/a (n=96) |
| hybrid-gliner-kev | 74.0% / 78.2% (n=466) | 62.9% / 77.4% (n=111) | 65.6% / 78.3% (n=116) |
| kev-0.8b | 74.0% / n/a (n=261) | 62.9% / n/a (n=89) | 65.6% / n/a (n=96) |
| gliner2.5-small | 71.7% / 66.9% (n=466) | 37.1% / 45.2% (n=111) | 53.1% / 58.0% (n=116) |
| qwen3.5-4b | 82.4% / 90.1% (n=466) | 83.2% / 91.9% (n=111) | 77.1% / 94.2% (n=116) |

## Input length (long group, extraction exact match / latency p50 ms / unsupported)

| Model | tokens | position | n | EM | p50 ms | unsupported |
|---|---|---|---|---|---|---|
| hybrid-gliner-kev-qwen | 100 | end | 7 | 71.4% | 90.17 | 0 |
| hybrid-gliner-kev-qwen | 100 | middle | 6 | 33.3% | 89.60 | 0 |
| hybrid-gliner-kev-qwen | 100 | start | 7 | 85.7% | 89.91 | 0 |
| hybrid-gliner-kev-qwen | 500 | end | 5 | 60.0% | 195.88 | 0 |
| hybrid-gliner-kev-qwen | 500 | middle | 7 | 0.0% | 200.57 | 0 |
| hybrid-gliner-kev-qwen | 500 | start | 7 | 42.9% | 200.09 | 0 |
| hybrid-gliner-kev-qwen | 2000 | end | 5 | 20.0% | 910.24 | 0 |
| hybrid-gliner-kev-qwen | 2000 | middle | 5 | 60.0% | 910.12 | 0 |
| hybrid-gliner-kev-qwen | 2000 | start | 7 | 57.1% | 925.40 | 0 |
| hybrid-gliner-kev-qwen | 5000 | end | 6 | 66.7% | 4769.65 | 0 |
| hybrid-gliner-kev-qwen | 5000 | middle | 4 | 25.0% | 4624.64 | 0 |
| hybrid-gliner-kev-qwen | 5000 | start | 4 | 50.0% | 3199.13 | 0 |
| gliner2.5-multi | 100 | end | 7 | 28.6% | 98.46 | 0 |
| gliner2.5-multi | 100 | middle | 6 | 0.0% | 98.86 | 0 |
| gliner2.5-multi | 100 | start | 7 | 42.9% | 99.20 | 0 |
| gliner2.5-multi | 500 | end | 5 | 0.0% | 255.24 | 0 |
| gliner2.5-multi | 500 | middle | 7 | 0.0% | 273.02 | 0 |
| gliner2.5-multi | 500 | start | 7 | 0.0% | 280.86 | 0 |
| gliner2.5-multi | 2000 | end | 5 | 0.0% | 1588.42 | 0 |
| gliner2.5-multi | 2000 | middle | 5 | 0.0% | 1567.05 | 0 |
| gliner2.5-multi | 2000 | start | 7 | 0.0% | 1634.07 | 0 |
| gliner2.5-multi | 5000 | end | 6 | 0.0% | 2467.35 | 0 |
| gliner2.5-multi | 5000 | middle | 4 | 0.0% | 2460.58 | 0 |
| gliner2.5-multi | 5000 | start | 4 | 0.0% | 2512.13 | 0 |
| nuextract3 | 100 | end | 7 | 100.0% | 1230.00 | 0 |
| nuextract3 | 100 | middle | 6 | 100.0% | 1208.84 | 0 |
| nuextract3 | 100 | start | 7 | 100.0% | 1476.15 | 0 |
| nuextract3 | 500 | end | 5 | 100.0% | 1611.62 | 0 |
| nuextract3 | 500 | middle | 7 | 100.0% | 1730.49 | 0 |
| nuextract3 | 500 | start | 7 | 100.0% | 1751.99 | 0 |
| nuextract3 | 2000 | end | 5 | 100.0% | 3236.23 | 0 |
| nuextract3 | 2000 | middle | 5 | 100.0% | 3235.23 | 0 |
| nuextract3 | 2000 | start | 7 | 100.0% | 3177.46 | 0 |
| nuextract3 | 5000 | end | 6 | 100.0% | 6138.76 | 0 |
| nuextract3 | 5000 | middle | 4 | 100.0% | n/a | 0 |
| nuextract3 | 5000 | start | 4 | 100.0% | 6151.42 | 0 |
| gliner2.5-base | 100 | end | 7 | 71.4% | 84.38 | 0 |
| gliner2.5-base | 100 | middle | 6 | 33.3% | 83.91 | 0 |
| gliner2.5-base | 100 | start | 7 | 85.7% | 84.32 | 0 |
| gliner2.5-base | 500 | end | 5 | 60.0% | 184.15 | 0 |
| gliner2.5-base | 500 | middle | 7 | 0.0% | 183.45 | 0 |
| gliner2.5-base | 500 | start | 7 | 42.9% | 183.63 | 0 |
| gliner2.5-base | 2000 | end | 5 | 20.0% | 912.03 | 0 |
| gliner2.5-base | 2000 | middle | 5 | 60.0% | 906.72 | 0 |
| gliner2.5-base | 2000 | start | 7 | 57.1% | 907.29 | 0 |
| gliner2.5-base | 5000 | end | 6 | 66.7% | 4791.43 | 0 |
| gliner2.5-base | 5000 | middle | 4 | 25.0% | 4658.71 | 0 |
| gliner2.5-base | 5000 | start | 4 | 50.0% | 3227.61 | 0 |
| hybrid-gliner-qwen | 100 | end | 7 | 71.4% | 107.01 | 0 |
| hybrid-gliner-qwen | 100 | middle | 6 | 33.3% | 103.68 | 0 |
| hybrid-gliner-qwen | 100 | start | 7 | 85.7% | 107.98 | 0 |
| hybrid-gliner-qwen | 500 | end | 5 | 60.0% | 226.36 | 0 |
| hybrid-gliner-qwen | 500 | middle | 7 | 0.0% | 227.60 | 0 |
| hybrid-gliner-qwen | 500 | start | 7 | 42.9% | 237.16 | 0 |
| hybrid-gliner-qwen | 2000 | end | 5 | 20.0% | 1117.85 | 0 |
| hybrid-gliner-qwen | 2000 | middle | 5 | 60.0% | 1139.63 | 0 |
| hybrid-gliner-qwen | 2000 | start | 7 | 57.1% | 1139.48 | 0 |
| hybrid-gliner-qwen | 5000 | end | 6 | 66.7% | 5456.71 | 0 |
| hybrid-gliner-qwen | 5000 | middle | 4 | 25.0% | 5558.18 | 0 |
| hybrid-gliner-qwen | 5000 | start | 4 | 50.0% | 3725.30 | 0 |
| kev-0.8b-candsel | 100 | end | 7 | 0.0% | 560.91 | 0 |
| kev-0.8b-candsel | 100 | middle | 6 | 0.0% | 553.61 | 0 |
| kev-0.8b-candsel | 100 | start | 7 | 14.3% | 556.06 | 0 |
| kev-0.8b-candsel | 500 | end | 5 | 20.0% | 624.97 | 0 |
| kev-0.8b-candsel | 500 | middle | 7 | 28.6% | 625.22 | 0 |
| kev-0.8b-candsel | 500 | start | 7 | 14.3% | 630.22 | 0 |
| kev-0.8b-candsel | 2000 | end | 5 | 0.0% | 882.02 | 0 |
| kev-0.8b-candsel | 2000 | middle | 5 | 20.0% | 883.61 | 0 |
| kev-0.8b-candsel | 2000 | start | 7 | 0.0% | 884.05 | 0 |
| kev-0.8b-candsel | 5000 | end | 6 | 0.0% | 1434.21 | 0 |
| kev-0.8b-candsel | 5000 | middle | 4 | 50.0% | 1440.83 | 0 |
| kev-0.8b-candsel | 5000 | start | 4 | 25.0% | 1449.58 | 0 |
| hybrid-gliner-kev | 100 | end | 7 | 71.4% | 87.25 | 0 |
| hybrid-gliner-kev | 100 | middle | 6 | 33.3% | 86.83 | 0 |
| hybrid-gliner-kev | 100 | start | 7 | 85.7% | 86.84 | 0 |
| hybrid-gliner-kev | 500 | end | 5 | 60.0% | 187.75 | 0 |
| hybrid-gliner-kev | 500 | middle | 7 | 0.0% | 189.52 | 0 |
| hybrid-gliner-kev | 500 | start | 7 | 42.9% | 188.84 | 0 |
| hybrid-gliner-kev | 2000 | end | 5 | 20.0% | 915.28 | 0 |
| hybrid-gliner-kev | 2000 | middle | 5 | 60.0% | 910.79 | 0 |
| hybrid-gliner-kev | 2000 | start | 7 | 57.1% | 914.60 | 0 |
| hybrid-gliner-kev | 5000 | end | 6 | 66.7% | 4650.03 | 0 |
| hybrid-gliner-kev | 5000 | middle | 4 | 25.0% | 4501.88 | 0 |
| hybrid-gliner-kev | 5000 | start | 4 | 50.0% | 3208.53 | 0 |
| gliner2.5-small | 100 | end | 7 | 100.0% | 49.19 | 0 |
| gliner2.5-small | 100 | middle | 6 | 50.0% | 48.77 | 0 |
| gliner2.5-small | 100 | start | 7 | 28.6% | 49.34 | 0 |
| gliner2.5-small | 500 | end | 5 | 0.0% | 100.63 | 0 |
| gliner2.5-small | 500 | middle | 7 | 0.0% | 102.22 | 0 |
| gliner2.5-small | 500 | start | 7 | 0.0% | 100.18 | 0 |
| gliner2.5-small | 2000 | end | 5 | 20.0% | 504.06 | 0 |
| gliner2.5-small | 2000 | middle | 5 | 0.0% | 481.38 | 0 |
| gliner2.5-small | 2000 | start | 7 | 0.0% | 489.43 | 0 |
| gliner2.5-small | 5000 | end | 6 | 66.7% | 2477.71 | 0 |
| gliner2.5-small | 5000 | middle | 4 | 50.0% | 2442.19 | 0 |
| gliner2.5-small | 5000 | start | 4 | 0.0% | 1683.57 | 0 |
| qwen3.5-4b | 100 | end | 7 | 85.7% | 1266.92 | 0 |
| qwen3.5-4b | 100 | middle | 6 | 83.3% | 1246.31 | 0 |
| qwen3.5-4b | 100 | start | 7 | 85.7% | 1581.98 | 0 |
| qwen3.5-4b | 500 | end | 5 | 80.0% | 1600.03 | 0 |
| qwen3.5-4b | 500 | middle | 7 | 42.9% | 1033.98 | 0 |
| qwen3.5-4b | 500 | start | 7 | 14.3% | 978.16 | 0 |
| qwen3.5-4b | 2000 | end | 5 | 100.0% | 3206.21 | 0 |
| qwen3.5-4b | 2000 | middle | 5 | 100.0% | 3203.79 | 0 |
| qwen3.5-4b | 2000 | start | 7 | 0.0% | 2269.10 | 0 |
| qwen3.5-4b | 5000 | end | 6 | 100.0% | 5541.72 | 0 |
| qwen3.5-4b | 5000 | middle | 4 | 100.0% | 5847.72 | 0 |
| qwen3.5-4b | 5000 | start | 4 | 0.0% | 4927.18 | 0 |

## Resources (sampled during this model's phases)

| Model | RSS mean MB | RSS peak MB | CPU mean % | CPU peak % | Ollama loaded size | storage | note |
|---|---|---|---|---|---|---|---|
| hybrid-gliner-kev-qwen | n/a | n/a | n/a | n/a | kev:0.8b-mlx-bf16: 9.46 GiB qwen3.5:4b: 11.71 GiB | n/a | not sampled (pipeline or remote endpoint; see stage models) |
| gliner2.5-multi | 2042.40 | 4376.50 | 325.00 | 553.70 | n/a | 1.09 GiB | ps sampling every 500 ms; CPU% is per-core percentage summed over matched processes (can exceed 100) |
| nuextract3 | 8774.20 | 13020.80 | 283.20 | 465.20 | numind/nuextract3:q4_k_m: 3.49 GiB | 3.15 GiB | ps sampling every 500 ms; CPU% is per-core percentage summed over matched processes (can exceed 100) |
| gliner2.5-base | 5097.40 | 10627.30 | 392.20 | 684.20 | n/a | 0.73 GiB | ps sampling every 500 ms; CPU% is per-core percentage summed over matched processes (can exceed 100) |
| hybrid-gliner-qwen | n/a | n/a | n/a | n/a | qwen3.5:4b: 11.72 GiB | n/a | not sampled (pipeline or remote endpoint; see stage models) |
| kev-0.8b-candsel | 1407.00 | 1418.00 | 46.90 | 106.10 | kev:0.8b-mlx-bf16: 9.52 GiB | 1.42 GiB | ps sampling every 500 ms; CPU% is per-core percentage summed over matched processes (can exceed 100) |
| gliner2.5-decide | 1758.20 | 1773.60 | 256.60 | 272.90 | n/a | 1.82 GiB | ps sampling every 500 ms; CPU% is per-core percentage summed over matched processes (can exceed 100) |
| hybrid-gliner-kev | n/a | n/a | n/a | n/a | kev:0.8b-mlx-bf16: 9.46 GiB | n/a | not sampled (pipeline or remote endpoint; see stage models) |
| kev-0.8b | 1419.30 | 1657.50 | 61.60 | 125.00 | kev:0.8b-mlx-bf16: 9.46 GiB | 1.42 GiB | ps sampling every 500 ms; CPU% is per-core percentage summed over matched processes (can exceed 100) |
| gliner2.5-small | 3590.30 | 7958.70 | 381.10 | 672.40 | n/a | 0.28 GiB | ps sampling every 500 ms; CPU% is per-core percentage summed over matched processes (can exceed 100) |
| qwen3.5-4b | 3612.90 | 3619.40 | 51.60 | 172.80 | qwen3.5:4b: 11.85 GiB | 3.70 GiB | ps sampling every 500 ms; CPU% is per-core percentage summed over matched processes (can exceed 100) |

GPU utilization and energy: not measured (no reliable unprivileged method on this platform).

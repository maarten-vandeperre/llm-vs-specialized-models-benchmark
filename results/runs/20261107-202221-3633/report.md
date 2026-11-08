# Benchmark report 20261107-202221-3633

> **Synthetic benchmark.** Synthetic, fictional coffee-shop dataset. Results do not generalize to production customers.

- Status: COMPLETED, created 2026-11-07T20:22:21.359382Z, finished 2026-11-07T21:50:17.471047Z
- Dataset: `coffee-v1` split `evaluation`, groups all, limit null, records selected 693, sha256 `627e6d79f1b9c54031c6f9c421686e108c133d2327338c87956cc99db631d49d`
- Method: warm-up 10, repetitions 5, latency sample 30, concurrency [1, 4, 8], throughput requests 40, cold start true, seed 42, timeout 180 s
- Model order (seeded shuffle): [hybrid-gliner-kev-qwen, gliner2.5-multi, nuextract3, gliner2.5-base, hybrid-gliner-qwen, kev-0.8b-candsel, gliner2.5-decide, hybrid-gliner-kev, kev-0.8b, gliner2.5-small, qwen3.5-4b]
- Hardware: Apple M1 Max, 64.00 GiB memory, 10 CPUs, Mac OS X 26.6.2
- Software: Java 25.0.2, Quarkus 3.40.1.1, Ollama 0.40.15, GLiNER service {python=3.12.14, gliner2=2.1.0, torch=2.15.0, transformers=5.22.2}
- Hybrid thresholds: {kev=0.5862, source=/Users/maartenvandeperre/workspace/redhat/temp-workshop/benchmark-platform/results/calibration/thresholds.json (calibration split), gliner=0.9649}

## Quality (quality phase, failures counted as errors)

| Model | n | Intent acc [95% CI] | Intent macro-F1 [CI] | Mean field acc | Extraction EM [CI] | Entity F1 | Halluc. rate | Missing rate | Decision acc | Failures (unsup/malf/timeout/infra) |
|---|---|---|---|---|---|---|---|---|---|---|
| hybrid-gliner-kev-qwen | 693 | 81.8% [78.0%–85.1%] | 0.82 [0.79–0.85] | 87.5% | 78.1% [73.9%–81.7%] | 0.95 | 0.0% | 4.3% | 96.6% | 0/0/0/0 |
| gliner2.5-multi | 693 | 66.8% [62.3%–71.0%] | 0.65 [0.62–0.68] | 44.1% | 60.1% [55.4%–64.7%] | 0.82 | 0.0% | 7.8% | 78.0% | 0/0/0/0 |
| nuextract3 | 693 | 79.1% [75.1%–82.7%] | 0.78 [0.74–0.80] | 80.2% | 99.5% [98.3%–99.9%] | 1.00 | 0.0% | 0.1% | 93.2% | 0/0/0/0 |
| gliner2.5-base | 693 | 62.8% [58.2%–67.1%] | 0.68 [0.65–0.72] | 66.6% | 76.6% [72.4%–80.4%] | 0.94 | 0.0% | 4.4% | 71.2% | 0/0/0/0 |
| hybrid-gliner-qwen | 693 | 81.2% [77.3%–84.5%] | 0.81 [0.78–0.84] | 86.3% | 80.9% [76.9%–84.4%] | 0.95 | 0.1% | 4.2% | 96.6% | 0/0/0/0 |
| kev-0.8b-candsel | 693 | 70.0% [65.5%–74.0%] | 0.75 [0.72–0.78] | 79.4% | 19.6% [16.1%–23.6%] | 0.73 | 24.3% | 8.2% | 78.0% | 0/0/0/0 |
| gliner2.5-decide | 446 | 75.8% [71.6%–79.5%] | 0.75 [0.72–0.77] | 66.6% | – | – | – | – | – | 0/0/0/0 |
| hybrid-gliner-kev | 693 | 70.0% [65.5%–74.0%] | 0.75 [0.72–0.78] | 79.4% | 78.1% [73.9%–81.7%] | 0.95 | 0.0% | 4.3% | 80.8% | 0/0/0/0 |
| kev-0.8b | 446 | 70.0% [65.5%–74.0%] | 0.75 [0.72–0.78] | 79.4% | – | – | – | – | – | 0/0/0/0 |
| gliner2.5-small | 693 | 60.8% [56.2%–65.2%] | 0.59 [0.55–0.63] | 43.4% | 62.3% [57.6%–66.8%] | 0.89 | 0.6% | 2.9% | 67.2% | 0/0/0/0 |
| qwen3.5-4b | 693 | 81.8% [78.0%–85.1%] | 0.82 [0.79–0.85] | 88.7% | 91.3% [88.2%–93.6%] | 0.96 | 0.2% | 6.6% | 96.6% | 0/0/0/0 |

## Per-field classification accuracy

| Model | intent | sentiment | category | complaint | all four correct |
|---|---|---|---|---|---|
| hybrid-gliner-kev-qwen | 81.8% | 91.5% | 82.3% | 94.2% | 60.5% |
| gliner2.5-multi | 66.8% | 41.3% | 24.7% | 43.7% | 4.0% |
| nuextract3 | 79.1% | 92.8% | 65.5% | 83.4% | 55.2% |
| gliner2.5-base | 62.8% | 60.8% | 71.8% | 71.1% | 27.4% |
| hybrid-gliner-qwen | 81.2% | 87.7% | 82.3% | 94.2% | 58.5% |
| kev-0.8b-candsel | 70.0% | 86.3% | 74.2% | 87.2% | 38.3% |
| gliner2.5-decide | 75.8% | 66.1% | 56.5% | 67.9% | 16.6% |
| hybrid-gliner-kev | 70.0% | 86.3% | 74.2% | 87.2% | 38.3% |
| kev-0.8b | 70.0% | 86.3% | 74.2% | 87.2% | 38.3% |
| gliner2.5-small | 60.8% | 39.5% | 28.0% | 45.3% | 3.6% |
| qwen3.5-4b | 81.8% | 94.4% | 84.5% | 94.2% | 65.5% |

## Extraction field accuracy

| Model | orderId | items | total | currency | offset acc (n) |
|---|---|---|---|---|---|
| hybrid-gliner-kev-qwen | 99.8% | 78.3% | 99.8% | 99.8% | 100.0% (387) |
| gliner2.5-multi | 100.0% | 60.1% | 100.0% | 100.0% | 100.0% (387) |
| nuextract3 | 99.8% | 99.8% | 100.0% | 100.0% | n/a |
| gliner2.5-base | 99.1% | 77.6% | 99.8% | 99.8% | 99.2% (387) |
| hybrid-gliner-qwen | 99.3% | 81.1% | 99.8% | 99.8% | 100.0% (272) |
| kev-0.8b-candsel | 99.5% | 21.9% | 84.9% | 88.4% | n/a |
| hybrid-gliner-kev | 99.8% | 78.3% | 99.8% | 99.8% | 100.0% (387) |
| gliner2.5-small | 95.3% | 66.3% | 96.5% | 97.9% | 96.9% (384) |
| qwen3.5-4b | 93.9% | 91.3% | 94.3% | 94.3% | n/a |

## Latency (client-side, concurrency 1)

Headline columns: each evaluation input sent once (quality phase), successful requests, full prompt-cache hits excluded. "repeat p50" is the latency-phase median over repeated identical inputs, which a runtime may serve from its prompt cache.

| Model | n | p50 ms [CI] | p90 | p95 | p99 | server p50 ms | cold start ms (load ms) | out tok/s | repeat p50 ms | cache full hits (first-seen / repeat) |
|---|---|---|---|---|---|---|---|---|---|---|
| hybrid-gliner-kev-qwen | 659 | 809.34 [802.21–827.12] | 1063.43 | 1163.29 | 4774.94 | n/a | 4609.25 (n/a) | n/a | 589.68 | 34 / 90 of 120 |
| gliner2.5-multi | 693 | 194.42 [192.65–196.10] | 255.98 | 278.65 | 2439.84 | 192.93 | 416.03 (n/a) | n/a | 191.86 | 0 / 0 of 120 |
| nuextract3 | 692 | 1467.65 [1425.22–1490.53] | 1826.38 | 1946.61 | 6111.35 | 1464.80 | 6053.11 (4592.37) | 68.66 | 1254.09 | 1 / 0 of 120 |
| gliner2.5-base | 693 | 158.91 [157.81–160.25] | 202.89 | 223.59 | 4732.21 | 157.68 | 177.95 (n/a) | n/a | 157.15 | 0 / 0 of 120 |
| hybrid-gliner-qwen | 661 | 785.38 [763.36–791.72] | 2182.87 | 2298.13 | 4661.28 | n/a | 2511.77 (n/a) | n/a | 755.59 | 32 / 66 of 120 |
| kev-0.8b-candsel | 659 | 542.42 [536.25–544.63] | 703.63 | 718.50 | 1442.26 | n/a | 711.37 (n/a) | n/a | 4.20 | 34 / 90 of 120 |
| gliner2.5-decide | 446 | 464.11 [460.94–466.38] | 499.19 | 510.37 | 523.35 | 462.98 | 859.09 (n/a) | n/a | 457.60 | 0 / 0 of 120 |
| hybrid-gliner-kev | 659 | 180.00 [179.25–180.91] | 302.66 | 313.02 | 4621.45 | n/a | 703.52 (n/a) | n/a | 4.26 | 34 / 90 of 120 |
| kev-0.8b | 412 | 181.19 [180.86–181.72] | 189.44 | 190.57 | 194.43 | n/a | 727.03 (n/a) | n/a | 4.38 | 34 / 90 of 120 |
| gliner2.5-small | 693 | 83.79 [83.22–84.40] | 107.17 | 116.35 | 2457.61 | 82.66 | 174.58 (n/a) | n/a | 83.49 | 0 / 0 of 120 |
| qwen3.5-4b | 658 | 1418.92 [1238.62–1525.12] | 2036.91 | 2112.58 | 5586.10 | 1418.16 | 1845.16 (810.53) | 69.71 | 605.70 | 35 / 90 of 120 |

## Throughput

Each concurrency level uses its own window of inputs not used by the warm-up or latency phases.

| Model | concurrency | requests | wall s | req/s | p50 ms | p95 ms | errors | cache full hits |
|---|---|---|---|---|---|---|---|---|
| hybrid-gliner-kev-qwen | 1 | 40 | 18.45 | 2.17 | 590.80 | 848.67 | 0 | 30 |
| hybrid-gliner-kev-qwen | 4 | 40 | 16.01 | 2.50 | 2345.28 | 2386.72 | 0 | 40 |
| hybrid-gliner-kev-qwen | 8 | 40 | 16.02 | 2.50 | 4410.61 | 4754.98 | 0 | 40 |
| gliner2.5-multi | 1 | 40 | 7.66 | 5.22 | 190.70 | 203.61 | 0 | 0 |
| gliner2.5-multi | 4 | 40 | 7.78 | 5.14 | 774.76 | 811.52 | 0 | 0 |
| gliner2.5-multi | 8 | 40 | 7.82 | 5.11 | 1542.28 | 1616.00 | 0 | 0 |
| nuextract3 | 1 | 40 | 49.20 | 0.81 | 1253.60 | 1475.94 | 0 | 0 |
| nuextract3 | 4 | 40 | 49.14 | 0.81 | 4891.91 | 5439.47 | 0 | 0 |
| nuextract3 | 8 | 40 | 49.13 | 0.81 | 9698.42 | 10241.30 | 0 | 0 |
| gliner2.5-base | 1 | 40 | 6.40 | 6.25 | 157.93 | 169.30 | 0 | 0 |
| gliner2.5-base | 4 | 40 | 6.33 | 6.32 | 628.65 | 655.25 | 0 | 0 |
| gliner2.5-base | 8 | 40 | 6.45 | 6.20 | 1269.53 | 1365.19 | 0 | 0 |
| hybrid-gliner-qwen | 1 | 40 | 23.67 | 1.69 | 747.77 | 811.34 | 0 | 22 |
| hybrid-gliner-qwen | 4 | 40 | 17.02 | 2.35 | 2202.38 | 2429.69 | 0 | 28 |
| hybrid-gliner-qwen | 8 | 40 | 16.98 | 2.36 | 3876.54 | 4814.49 | 0 | 28 |
| kev-0.8b-candsel | 1 | 40 | 1.90 | 21.11 | 3.88 | 178.54 | 0 | 30 |
| kev-0.8b-candsel | 4 | 40 | 0.13 | 312.50 | 11.68 | 14.44 | 0 | 40 |
| kev-0.8b-candsel | 8 | 40 | 0.12 | 347.83 | 21.86 | 26.34 | 0 | 40 |
| gliner2.5-decide | 1 | 40 | 18.36 | 2.18 | 457.32 | 490.55 | 0 | 0 |
| gliner2.5-decide | 4 | 40 | 18.50 | 2.16 | 1829.73 | 1931.32 | 0 | 0 |
| gliner2.5-decide | 8 | 40 | 18.45 | 2.17 | 3668.43 | 3719.18 | 0 | 0 |
| hybrid-gliner-kev | 1 | 40 | 1.98 | 20.21 | 4.25 | 184.33 | 0 | 30 |
| hybrid-gliner-kev | 4 | 40 | 0.12 | 327.87 | 10.87 | 13.98 | 0 | 40 |
| hybrid-gliner-kev | 8 | 40 | 0.12 | 338.98 | 23.05 | 23.75 | 0 | 40 |
| kev-0.8b | 1 | 40 | 1.96 | 20.46 | 4.16 | 184.39 | 0 | 30 |
| kev-0.8b | 4 | 40 | 0.12 | 325.20 | 11.19 | 13.66 | 0 | 40 |
| kev-0.8b | 8 | 40 | 0.11 | 363.64 | 21.05 | 23.32 | 0 | 40 |
| gliner2.5-small | 1 | 40 | 3.39 | 11.80 | 83.70 | 90.87 | 0 | 0 |
| gliner2.5-small | 4 | 40 | 3.34 | 11.96 | 333.57 | 342.68 | 0 | 0 |
| gliner2.5-small | 8 | 40 | 3.48 | 11.50 | 678.69 | 739.79 | 0 | 0 |
| qwen3.5-4b | 1 | 40 | 24.43 | 1.64 | 597.06 | 674.35 | 0 | 30 |
| qwen3.5-4b | 4 | 40 | 23.84 | 1.68 | 2385.43 | 2413.47 | 0 | 40 |
| qwen3.5-4b | 8 | 40 | 23.81 | 1.68 | 4752.37 | 4799.53 | 0 | 40 |

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
| kev-0.8b-candsel | 64 | 21.9% | 78.1% | 84.5% |
| gliner2.5-decide | 64 | 0.0% | 100.0% | 99.6% |
| hybrid-gliner-kev | 64 | 21.9% | 78.1% | 84.5% |
| kev-0.8b | 64 | 21.9% | 78.1% | 84.5% |
| gliner2.5-small | 64 | 6.3% | 93.8% | 96.6% |
| qwen3.5-4b | 64 | 25.0% | 75.0% | 95.7% |

## Per language (intent accuracy / extraction exact match)

| Model | en | nl | fr |
|---|---|---|---|
| hybrid-gliner-kev-qwen | 82.8% / 78.2% (n=466) | 83.2% / 77.4% (n=111) | 78.1% / 78.3% (n=116) |
| gliner2.5-multi | 68.6% / 57.7% (n=466) | 62.9% / 79.0% (n=111) | 65.6% / 53.6% (n=116) |
| nuextract3 | 78.2% / 100.0% (n=466) | 82.0% / 98.4% (n=111) | 79.2% / 98.6% (n=116) |
| gliner2.5-base | 68.2% / 77.8% (n=466) | 46.1% / 72.6% (n=111) | 63.5% / 75.4% (n=116) |
| hybrid-gliner-qwen | 82.4% / 78.8% (n=466) | 82.0% / 85.5% (n=111) | 77.1% / 85.5% (n=116) |
| kev-0.8b-candsel | 74.0% / 24.6% (n=466) | 62.9% / 11.3% (n=111) | 65.6% / 5.8% (n=116) |
| gliner2.5-decide | 78.5% / n/a (n=261) | 69.7% / n/a (n=89) | 74.0% / n/a (n=96) |
| hybrid-gliner-kev | 74.0% / 78.2% (n=466) | 62.9% / 77.4% (n=111) | 65.6% / 78.3% (n=116) |
| kev-0.8b | 74.0% / n/a (n=261) | 62.9% / n/a (n=89) | 65.6% / n/a (n=96) |
| gliner2.5-small | 71.7% / 66.9% (n=466) | 37.1% / 45.2% (n=111) | 53.1% / 58.0% (n=116) |
| qwen3.5-4b | 82.8% / 90.4% (n=466) | 83.2% / 91.9% (n=111) | 78.1% / 94.2% (n=116) |

## Input length (long group, extraction exact match / latency p50 ms / unsupported)

| Model | tokens | position | n | EM | p50 ms | unsupported |
|---|---|---|---|---|---|---|
| hybrid-gliner-kev-qwen | 100 | end | 7 | 71.4% | 95.72 | 0 |
| hybrid-gliner-kev-qwen | 100 | middle | 6 | 33.3% | 98.10 | 0 |
| hybrid-gliner-kev-qwen | 100 | start | 7 | 85.7% | 101.57 | 0 |
| hybrid-gliner-kev-qwen | 500 | end | 5 | 60.0% | 227.70 | 0 |
| hybrid-gliner-kev-qwen | 500 | middle | 7 | 0.0% | 218.17 | 0 |
| hybrid-gliner-kev-qwen | 500 | start | 7 | 42.9% | 220.33 | 0 |
| hybrid-gliner-kev-qwen | 2000 | end | 5 | 20.0% | 1073.82 | 0 |
| hybrid-gliner-kev-qwen | 2000 | middle | 5 | 60.0% | 1082.61 | 0 |
| hybrid-gliner-kev-qwen | 2000 | start | 7 | 57.1% | 1135.06 | 0 |
| hybrid-gliner-kev-qwen | 5000 | end | 6 | 66.7% | 4693.05 | 0 |
| hybrid-gliner-kev-qwen | 5000 | middle | 4 | 25.0% | 4766.39 | 0 |
| hybrid-gliner-kev-qwen | 5000 | start | 4 | 50.0% | 3839.06 | 0 |
| gliner2.5-multi | 100 | end | 7 | 28.6% | 100.19 | 0 |
| gliner2.5-multi | 100 | middle | 6 | 0.0% | 98.74 | 0 |
| gliner2.5-multi | 100 | start | 7 | 42.9% | 99.80 | 0 |
| gliner2.5-multi | 500 | end | 5 | 0.0% | 259.84 | 0 |
| gliner2.5-multi | 500 | middle | 7 | 0.0% | 265.47 | 0 |
| gliner2.5-multi | 500 | start | 7 | 0.0% | 266.50 | 0 |
| gliner2.5-multi | 2000 | end | 5 | 0.0% | 1560.10 | 0 |
| gliner2.5-multi | 2000 | middle | 5 | 0.0% | 1570.17 | 0 |
| gliner2.5-multi | 2000 | start | 7 | 0.0% | 1554.43 | 0 |
| gliner2.5-multi | 5000 | end | 6 | 0.0% | 2442.61 | 0 |
| gliner2.5-multi | 5000 | middle | 4 | 0.0% | 2453.12 | 0 |
| gliner2.5-multi | 5000 | start | 4 | 0.0% | 2493.81 | 0 |
| nuextract3 | 100 | end | 7 | 100.0% | 1225.75 | 0 |
| nuextract3 | 100 | middle | 6 | 100.0% | 1196.62 | 0 |
| nuextract3 | 100 | start | 7 | 100.0% | 1387.25 | 0 |
| nuextract3 | 500 | end | 5 | 100.0% | 1614.04 | 0 |
| nuextract3 | 500 | middle | 7 | 100.0% | 1724.59 | 0 |
| nuextract3 | 500 | start | 7 | 100.0% | 1749.68 | 0 |
| nuextract3 | 2000 | end | 5 | 100.0% | 3230.77 | 0 |
| nuextract3 | 2000 | middle | 5 | 100.0% | 3222.63 | 0 |
| nuextract3 | 2000 | start | 7 | 100.0% | 3163.55 | 0 |
| nuextract3 | 5000 | end | 6 | 100.0% | 6074.04 | 0 |
| nuextract3 | 5000 | middle | 4 | 100.0% | 6231.58 | 0 |
| nuextract3 | 5000 | start | 4 | 100.0% | 6177.12 | 0 |
| gliner2.5-base | 100 | end | 7 | 71.4% | 83.76 | 0 |
| gliner2.5-base | 100 | middle | 6 | 33.3% | 83.41 | 0 |
| gliner2.5-base | 100 | start | 7 | 85.7% | 84.21 | 0 |
| gliner2.5-base | 500 | end | 5 | 60.0% | 182.36 | 0 |
| gliner2.5-base | 500 | middle | 7 | 0.0% | 185.38 | 0 |
| gliner2.5-base | 500 | start | 7 | 42.9% | 184.90 | 0 |
| gliner2.5-base | 2000 | end | 5 | 20.0% | 929.29 | 0 |
| gliner2.5-base | 2000 | middle | 5 | 60.0% | 921.49 | 0 |
| gliner2.5-base | 2000 | start | 7 | 57.1% | 912.67 | 0 |
| gliner2.5-base | 5000 | end | 6 | 66.7% | 4787.32 | 0 |
| gliner2.5-base | 5000 | middle | 4 | 25.0% | 4647.70 | 0 |
| gliner2.5-base | 5000 | start | 4 | 50.0% | 3221.81 | 0 |
| hybrid-gliner-qwen | 100 | end | 7 | 71.4% | 91.27 | 0 |
| hybrid-gliner-qwen | 100 | middle | 6 | 33.3% | 91.20 | 0 |
| hybrid-gliner-qwen | 100 | start | 7 | 85.7% | 91.46 | 0 |
| hybrid-gliner-qwen | 500 | end | 5 | 60.0% | 190.80 | 0 |
| hybrid-gliner-qwen | 500 | middle | 7 | 0.0% | 192.28 | 0 |
| hybrid-gliner-qwen | 500 | start | 7 | 42.9% | 192.40 | 0 |
| hybrid-gliner-qwen | 2000 | end | 5 | 20.0% | 1028.68 | 0 |
| hybrid-gliner-qwen | 2000 | middle | 5 | 60.0% | 915.97 | 0 |
| hybrid-gliner-qwen | 2000 | start | 7 | 57.1% | 914.06 | 0 |
| hybrid-gliner-qwen | 5000 | end | 6 | 66.7% | 4661.66 | 0 |
| hybrid-gliner-qwen | 5000 | middle | 4 | 25.0% | 4746.78 | 0 |
| hybrid-gliner-qwen | 5000 | start | 4 | 50.0% | 3184.47 | 0 |
| kev-0.8b-candsel | 100 | end | 7 | 0.0% | 555.36 | 0 |
| kev-0.8b-candsel | 100 | middle | 6 | 0.0% | 554.14 | 0 |
| kev-0.8b-candsel | 100 | start | 7 | 14.3% | 554.60 | 0 |
| kev-0.8b-candsel | 500 | end | 5 | 20.0% | 627.40 | 0 |
| kev-0.8b-candsel | 500 | middle | 7 | 28.6% | 622.51 | 0 |
| kev-0.8b-candsel | 500 | start | 7 | 14.3% | 625.03 | 0 |
| kev-0.8b-candsel | 2000 | end | 5 | 0.0% | 880.75 | 0 |
| kev-0.8b-candsel | 2000 | middle | 5 | 20.0% | 880.07 | 0 |
| kev-0.8b-candsel | 2000 | start | 7 | 0.0% | 883.05 | 0 |
| kev-0.8b-candsel | 5000 | end | 6 | 0.0% | 1436.24 | 0 |
| kev-0.8b-candsel | 5000 | middle | 4 | 50.0% | 1444.01 | 0 |
| kev-0.8b-candsel | 5000 | start | 4 | 25.0% | 1444.84 | 0 |
| hybrid-gliner-kev | 100 | end | 7 | 71.4% | 87.20 | 0 |
| hybrid-gliner-kev | 100 | middle | 6 | 33.3% | 86.23 | 0 |
| hybrid-gliner-kev | 100 | start | 7 | 85.7% | 86.92 | 0 |
| hybrid-gliner-kev | 500 | end | 5 | 60.0% | 188.79 | 0 |
| hybrid-gliner-kev | 500 | middle | 7 | 0.0% | 189.62 | 0 |
| hybrid-gliner-kev | 500 | start | 7 | 42.9% | 188.60 | 0 |
| hybrid-gliner-kev | 2000 | end | 5 | 20.0% | 914.12 | 0 |
| hybrid-gliner-kev | 2000 | middle | 5 | 60.0% | 910.66 | 0 |
| hybrid-gliner-kev | 2000 | start | 7 | 57.1% | 914.00 | 0 |
| hybrid-gliner-kev | 5000 | end | 6 | 66.7% | 4628.04 | 0 |
| hybrid-gliner-kev | 5000 | middle | 4 | 25.0% | 4528.00 | 0 |
| hybrid-gliner-kev | 5000 | start | 4 | 50.0% | 3203.66 | 0 |
| gliner2.5-small | 100 | end | 7 | 100.0% | 48.98 | 0 |
| gliner2.5-small | 100 | middle | 6 | 50.0% | 48.27 | 0 |
| gliner2.5-small | 100 | start | 7 | 28.6% | 49.22 | 0 |
| gliner2.5-small | 500 | end | 5 | 0.0% | 101.36 | 0 |
| gliner2.5-small | 500 | middle | 7 | 0.0% | 102.75 | 0 |
| gliner2.5-small | 500 | start | 7 | 0.0% | 102.05 | 0 |
| gliner2.5-small | 2000 | end | 5 | 20.0% | 479.89 | 0 |
| gliner2.5-small | 2000 | middle | 5 | 0.0% | 477.93 | 0 |
| gliner2.5-small | 2000 | start | 7 | 0.0% | 482.91 | 0 |
| gliner2.5-small | 5000 | end | 6 | 66.7% | 2481.83 | 0 |
| gliner2.5-small | 5000 | middle | 4 | 50.0% | 2464.91 | 0 |
| gliner2.5-small | 5000 | start | 4 | 0.0% | 1621.95 | 0 |
| qwen3.5-4b | 100 | end | 7 | 85.7% | 1263.12 | 0 |
| qwen3.5-4b | 100 | middle | 6 | 83.3% | 1239.64 | 0 |
| qwen3.5-4b | 100 | start | 7 | 85.7% | 1570.18 | 0 |
| qwen3.5-4b | 500 | end | 5 | 80.0% | 1592.38 | 0 |
| qwen3.5-4b | 500 | middle | 7 | 57.1% | 1579.13 | 0 |
| qwen3.5-4b | 500 | start | 7 | 14.3% | 974.44 | 0 |
| qwen3.5-4b | 2000 | end | 5 | 100.0% | 3286.58 | 0 |
| qwen3.5-4b | 2000 | middle | 5 | 100.0% | 3353.56 | 0 |
| qwen3.5-4b | 2000 | start | 7 | 0.0% | 2272.84 | 0 |
| qwen3.5-4b | 5000 | end | 6 | 100.0% | 5586.81 | 0 |
| qwen3.5-4b | 5000 | middle | 4 | 100.0% | 5912.12 | 0 |
| qwen3.5-4b | 5000 | start | 4 | 0.0% | 5007.09 | 0 |

## Resources (sampled during this model's phases)

| Model | RSS mean MB | RSS peak MB | CPU mean % | CPU peak % | Ollama loaded size | storage | note |
|---|---|---|---|---|---|---|---|
| hybrid-gliner-kev-qwen | n/a | n/a | n/a | n/a | kev:0.8b-mlx-bf16: 9.47 GiB qwen3.5:4b: 11.73 GiB | n/a | not sampled (pipeline or remote endpoint; see stage models) |
| gliner2.5-multi | 1732.20 | 4420.00 | 313.80 | 548.80 | n/a | 1.09 GiB | ps sampling every 500 ms; CPU% is per-core percentage summed over matched processes (can exceed 100) |
| nuextract3 | 6620.30 | 12814.40 | 275.60 | 468.50 | numind/nuextract3:q4_k_m: 3.49 GiB | 3.15 GiB | ps sampling every 500 ms; CPU% is per-core percentage summed over matched processes (can exceed 100) |
| gliner2.5-base | 3906.30 | 10468.30 | 353.30 | 690.40 | n/a | 0.73 GiB | ps sampling every 500 ms; CPU% is per-core percentage summed over matched processes (can exceed 100) |
| hybrid-gliner-qwen | n/a | n/a | n/a | n/a | qwen3.5:4b: 11.74 GiB | n/a | not sampled (pipeline or remote endpoint; see stage models) |
| kev-0.8b-candsel | 1411.20 | 1419.70 | 45.70 | 109.20 | kev:0.8b-mlx-bf16: 9.45 GiB | 1.42 GiB | ps sampling every 500 ms; CPU% is per-core percentage summed over matched processes (can exceed 100) |
| gliner2.5-decide | 1723.10 | 1773.30 | 255.50 | 270.70 | n/a | 1.82 GiB | ps sampling every 500 ms; CPU% is per-core percentage summed over matched processes (can exceed 100) |
| hybrid-gliner-kev | n/a | n/a | n/a | n/a | kev:0.8b-mlx-bf16: 9.47 GiB | n/a | not sampled (pipeline or remote endpoint; see stage models) |
| kev-0.8b | 1407.70 | 1646.00 | 60.90 | 138.90 | kev:0.8b-mlx-bf16: 9.47 GiB | 1.42 GiB | ps sampling every 500 ms; CPU% is per-core percentage summed over matched processes (can exceed 100) |
| gliner2.5-small | 2526.80 | 8062.70 | 337.80 | 666.50 | n/a | 0.28 GiB | ps sampling every 500 ms; CPU% is per-core percentage summed over matched processes (can exceed 100) |
| qwen3.5-4b | 3612.50 | 3618.80 | 53.30 | 119.60 | qwen3.5:4b: 11.78 GiB | 3.70 GiB | ps sampling every 500 ms; CPU% is per-core percentage summed over matched processes (can exceed 100) |

GPU utilization and energy: not measured (no reliable unprivileged method on this platform).

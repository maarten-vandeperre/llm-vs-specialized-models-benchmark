# Benchmark results

> **Synthetic benchmark.** Every number below comes from a fictional, deterministically generated coffee-shop
> dataset (`coffee-v1`, seed 42, English/Dutch/French). It shows how these models behave on *this* task design,
> not on your customers' messages. Re-run it on your own data before deciding anything.

All figures are copied from the generated reports, at most rounded for readability (whole milliseconds, "≈" for
ranges across cells). Nothing is estimated. The per-request evidence for every number is in `predictions.jsonl` / `predictions.csv` in the
same run directory.

## Runs

| Run | Where | Status | Use |
|---|---|---|---|
| [`20261107-215748-1a28`](../results/runs/20261107-215748-1a28/report.md) | MacBook (M1 Max) | COMPLETED, 9,890 calls, 0 failures | **canonical MacBook results** |
| [`20261107-220443-d679`](../results/runs/20261107-220443-d679/report.md) | OpenShift Developer Sandbox | COMPLETED, 944 calls, 0 failures | **canonical Sandbox results** (GLiNER2.5-base only) |
| [`20261108-073920-b0b4`](../results/runs/20261108-073920-b0b4/report.md) | MacBook client → OpenAI API | COMPLETED, 1,888 calls, 0 failures | **commercial API comparison** (GPT-6 Luna, GPT-6.1 Sol) |
| [`20261108-073925-fab2`](../results/runs/20261108-073925-fab2/report.md) | MacBook client → Anthropic API | COMPLETED, 1,888 calls, 0 failures | **commercial API comparison** (Claude Haiku 5.5, Claude Opus 5.5) |
| [`20261108-073625-beac`](../results/runs/20261108-073625-beac/SMOKE.md) | MacBook client → both APIs | smoke test, 20 records | adapter check only, not a result |
| [`20261107-202221-3633`](../results/runs/20261107-202221-3633/SUPERSEDED.md) | MacBook | superseded | Kev throughput measured the prompt cache (see below) |
| [`20261107-203609-bdb2`](../results/runs/20261107-203609-bdb2/SUPERSEDED.md) | Sandbox | superseded | GLiNER pod OOMKilled; latency/throughput invalid |

### Reproduce

```bash
# MacBook (Profile A): models pulled, GLiNER services running (scripts/start-gliner.sh all)
scripts/calibrate.sh                                          # thresholds from the calibration split
cd backend && java -jar target/quarkus-app/quarkus-run.jar benchmark \
  --warmup=10 --repetitions=5 --latency-sample=30 --concurrency=1,4,8 --throughput-requests=40 \
  --cold-start=true --seed=42 --label=full-macbook-v2 \
  --models=kev-0.8b,kev-0.8b-candsel,gliner2.5-base,gliner2.5-small,gliner2.5-multi,gliner2.5-decide,nuextract3,qwen3.5-4b,hybrid-gliner-kev,hybrid-gliner-kev-qwen,hybrid-gliner-qwen
```

```bash
# OpenShift Sandbox (Profile B), after deployments/openshift/deploy.sh <namespace>
curl -s https://$API/api/benchmarks -H 'Content-Type: application/json' -d '{
  "models":["gliner2.5-base"],"dataset":"coffee-v1","split":"evaluation","warmup":10,"repetitions":5,
  "latencySampleSize":30,"concurrency":[1,4,8],"throughputRequests":40,"timeoutSeconds":300,
  "coldStart":true,"seed":42,"label":"full-openshift-sandbox-v2"}'
```

```bash
# Commercial API comparison (opt-in; costs money, see "Cost" below). Keys only via the environment.
export OPENAI_API_KEY=... ANTHROPIC_API_KEY=...
MODELS=openai-gpt-6-luna,openai-gpt-6.1-sol scripts/full-benchmark.sh --label=full-cloud-openai
MODELS=claude-haiku-5.5,claude-opus-5.5   scripts/full-benchmark.sh --label=full-cloud-anthropic
scripts/cloud-cost.py <openai-runId> <anthropic-runId>
```

Wall-clock: about 1 h 34 min on the MacBook and 31 min in the Sandbox.

### Environment

| | MacBook | OpenShift Sandbox |
|---|---|---|
| Hardware | Apple M1 Max (8P+2E cores), 64 GB unified memory | shared AWS node, Intel Xeon Platinum 8175M @ 2.5 GHz as seen by the backend pod (1 CPU limit); GLiNER pod limit 2 CPU / 12 Gi. The superseded run landed on an 8259CL node, so the node type varies |
| OS | macOS 26.6.2 | RHEL 9 kernel 5.14 (UBI images) |
| Runtimes | Java 25.0.2, Quarkus 3.40.1.1, Ollama 0.40.15, Python 3.12.14, gliner2 2.1.0, torch 2.15.0 | Java 25.0.5, Quarkus 3.40.1.1, Python 3.12.15, gliner2 2.1.0, torch 2.15.0+cpu |
| Ollama models (digest prefix) | `kev:0.8b-mlx-bf16` (BF16, f8bb3dfe), `qwen3.5:4b` (reported quantization `nvfp4`, 7f3251fa), `numind/nuextract3:q4_k_m` (Q4_K_M, d688f2bc) | none |
| GLiNER checkpoints | `fastino/gliner2.5-base-v1`, `-small`, `-multi`, `fastino/GLiNER2.5-Decide` (one process per checkpoint, CPU) | `fastino/gliner2.5-base-v1` |
| Dataset | `coffee-v1`, evaluation split, 693 records, sha256 `627e6d79…31d49d` | same |
| Hybrid thresholds | Kev 0.5862, GLiNER 0.9649 (calibration split, 208 records, target 95% selective accuracy) | not applicable |

**Power:** the MacBook ran on AC until 22:55 UTC and on battery after that, with battery energy mode set to
High Power (the same mode as on AC; [power log](../results/runs/20261107-215748-1a28/power-log.txt)). The
models measured on battery (GLiNER2.5-Decide, hybrid-gliner-kev, kev-0.8b, GLiNER2.5-small, Qwen3.5-4B) differ
from the earlier all-AC run by −1.5% to +1.7% p50. That is smaller than the run-to-run variation observed on AC
(NuExtract3: −7.4%), so the power source made no material difference.

## Quality (evaluation split, quality pass, failures count as errors)

| Candidate | Intent acc [95% CI] | Intent macro-F1 | All 4 labels correct | Extraction EM [95% CI] | Entity F1 | Halluc. | Decision acc |
|---|---|---|---|---|---|---|---|
| **qwen3.5-4b** (LLM) | 81.4% [77.5–84.7] | 0.81 | 64.6% | 91.0% [87.9–93.4] | 0.96 | 0.2% | 96.1% |
| **nuextract3** (extraction LLM) | 79.1% [75.1–82.7] | 0.78 | 55.4% | **99.8%** [98.7–100.0] | 1.00 | 0.0% | 93.8% |
| **kev-0.8b** (decision model) | 70.0% [65.5–74.0] | 0.75 | 38.3% | – | – | – | – |
| kev-0.8b-candsel (experiment) | 69.3% [64.9–73.4] | 0.75 | 37.4% | 20.8% [17.2–24.9] | 0.73 | 24.3% | 77.4% |
| **gliner2.5-base** (encoder) | 62.8% [58.2–67.1] | 0.68 | 27.4% | 76.6% [72.4–80.4] | 0.94 | 0.0% | 71.2% |
| gliner2.5-small | 60.8% [56.2–65.2] | 0.59 | 3.6% | 62.3% [57.6–66.8] | 0.89 | 0.6% | 67.2% |
| gliner2.5-multi | 66.8% [62.3–71.0] | 0.65 | 4.0% | 60.1% [55.4–64.7] | 0.82 | 0.0% | 78.0% |
| gliner2.5-decide (classification only) | 75.8% [71.6–79.5] | 0.75 | 16.6% | – | – | – | – |
| hybrid-gliner-kev | 70.0% [65.5–74.0] | 0.75 | 38.3% | 78.1% [73.9–81.7] | 0.95 | 0.0% | 80.8% |
| hybrid-gliner-kev-qwen | 81.8% [78.0–85.1] | 0.82 | 60.5% | 78.1% [73.9–81.7] | 0.95 | 0.0% | 96.6% |
| hybrid-gliner-qwen | 81.2% [77.3–84.5] | 0.81 | 58.5% | 80.9% [76.9–84.4] | 0.95 | 0.1% | 96.6% |

n = 693 records (446 for the classification-only candidates Kev and GLiNER2.5-Decide, which skip pure extraction
records). Zero failures for every candidate. Per-field accuracy:

| Candidate | intent | sentiment | category | complaint |
|---|---|---|---|---|
| qwen3.5-4b | 81.4% | 94.4% | 84.1% | 94.4% |
| nuextract3 | 79.1% | 92.8% | 65.7% | 83.4% |
| kev-0.8b | 70.0% | 86.3% | 74.2% | 87.2% |
| gliner2.5-decide | 75.8% | 66.1% | 56.5% | 67.9% |
| gliner2.5-base | 62.8% | 60.8% | 71.8% | 71.1% |
| gliner2.5-multi | 66.8% | 41.3% | 24.7% | 43.7% |
| gliner2.5-small | 60.8% | 39.5% | 28.0% | 45.3% |

## Speed (MacBook, client-side, concurrency 1)

The headline latency covers every evaluation input sent once (quality pass), successful requests only, with
prompt-reuse hits excluded (see "Measurement problems" below). That leaves n = 667–693 per candidate, or 421 for Kev.

| Candidate | p50 ms [CI] | p95 ms | p99 ms | p50 classification / combined / extraction | Throughput req/s at c=1 / 4 / 8 |
|---|---|---|---|---|---|
| gliner2.5-small | 85 [85–86] | 113 | 2,455 | 85 / 103 / 45 | 11.8 / 11.9 / 11.9 |
| gliner2.5-base | 161 [160–162] | 206 | 4,752 | 160 / 194 / 76 | 6.2 / 6.3 / 6.3 |
| kev-0.8b | 182 [182–182] | 191 | 194 | 182 / – / – | 5.6 / 5.6 / 5.9 |
| hybrid-gliner-kev | 180 [179–181] | 314 | 4,605 | 180 / 294 / 75 | 5.6 / 5.6 / 5.9 |
| gliner2.5-multi | 193 [191–194] | 291 | 2,474 | 191 / 239 / 77 | 5.2 / 5.2 / 5.2 |
| gliner2.5-decide | 467 [465–469] | 515 | 544 | 467 / – / – | 2.2 / 2.2 / 2.2 |
| kev-0.8b-candsel | 544 [536–548] | 714 | 1,440 | 181 / 694 / 548 | 5.7 / 5.6 / 5.9 |
| hybrid-gliner-qwen | 807 [785–817] | 2,520 | 5,332 | 827 / 2,018 / 79 | 1.4 / 1.9 / 1.9 |
| hybrid-gliner-kev-qwen | 815 [803–826] | 1,066 | 4,711 | 827 / 1,012 / 79 | 1.3 / 1.5 / 1.7 |
| nuextract3 | 1,359 [1,321–1,411] | 1,988 | 3,297 | 1,150 / 1,721 / 1,365 | 0.8 / 0.8 / 0.8 |
| qwen3.5-4b | 1,398 [1,237–1,528] | 2,113 | 5,526 | 650 / 1,874 / 1,587 | 1.5 / 1.5 / 1.5 |

Cold start (model unloaded, then first request): Kev 722 ms, Qwen3.5-4B 1,742 ms (Ollama load 711 ms), NuExtract3
6,303 ms (load 4,847 ms). GLiNER loads at service start, so its first request after warm start-up took
123–502 ms. Generation speed: Qwen 69.0 and NuExtract3 71.9 output tokens/s. Kev generates no text; its
`usage.output_tokens` is 0.

**Concurrency does not raise throughput for any single candidate on this machine.** Ollama serialized requests
to each model (server default, `OLLAMA_NUM_PARALLEL` unset), and the GLiNER service processes one request at a
time per checkpoint. Latency therefore grows linearly with concurrency: Qwen p50 is 646 ms at c=1 and
5,170 ms at c=8. Only the hybrids gain a little, because their stages run on different processes.

### Long inputs (long group, 70 evaluation records, 4 lengths × 3 positions)

Extraction exact match by target length and the position of the order information:

| Candidate | 100 tok s/m/e | 500 s/m/e | 2,000 s/m/e | 5,000 s/m/e | p50 ms at 5,000 tok |
|---|---|---|---|---|---|
| nuextract3 | 100/100/100% | 100/100/100% | 100/100/100% | 100/100/100% | ≈6,140 |
| qwen3.5-4b | 86/83/86% | **14**/43/80% | **0**/100/100% | **0**/100/100% | ≈4,930–5,850 |
| gliner2.5-base | 86/33/71% | 43/0/60% | 57/60/20% | 50/25/67% | ≈3,230–4,790 |
| gliner2.5-small | 29/50/100% | 0/0/0% | 0/0/20% | 0/50/67% | ≈1,680–2,480 |
| gliner2.5-multi | 43/0/29% | 0/0/0% | 0/0/0% | 0/0/0% | ≈2,460–2,510 |

(s = start, m = middle, e = end; 4–7 records per cell, so single cells are noisy.) Qwen3.5-4B fails systematically
when the order details come *first* and are followed by long unrelated text. It returns valid JSON with
`order_id`, `items`, `total_amount` and `currency` all null. The input was not truncated (Ollama reports the full
prompt token count, `truncate: false`, `num_ctx` 16,384). This was observed with thinking disabled, as configured
for the benchmark. NuExtract3 extracted every long document correctly.

## Confidence and abstention

Native label probabilities only. NuExtract3 and Qwen3.5-4B return none, and the benchmark does not invent any.

| Candidate | Brier | ECE | Risk at score ≥ 0.80 (coverage) |
|---|---|---|---|
| kev-0.8b | 0.17 | 0.20 | 3.2% (16.0%) |
| gliner2.5-decide | 0.21 | 0.19 | 0.0% (8.9%) |
| gliner2.5-base | 0.23 | 0.19 | 24.1% (69.7%) |
| gliner2.5-small | 0.27 | 0.18 | 39.4% (26.2%) |
| gliner2.5-multi | 0.30 | 0.26 | 37.5% (34.7%) |

"Risk at 0.80" means: of the labels with a score of at least 0.80, the share that is wrong. Kev's scores are
*conservative*: when it is confident it is usually right, but it is rarely confident. GLiNER2.5-base is
*overconfident*: a quarter of its ≥ 0.80 labels are wrong. Kev's `confidence` field is a normalized entropy
(1 − H(p)/ln N), not a calibrated probability. The benchmark uses the top-label probability.

On the 64 deliberately ambiguous records, the share predicted as intent `none`: GLiNER2.5-base 76.6% (but it
also over-predicts `none` elsewhere: overall coverage 65.0%), NuExtract3 59.4%, Qwen3.5-4B 26.6%, Kev 21.9%,
GLiNER2.5-small 6.3%, GLiNER2.5-multi 1.6%, GLiNER2.5-Decide 0.0%.

### Hybrid routing

* `hybrid-gliner-kev-qwen` sent **55.6%** of records to the Qwen fallback, and `hybrid-gliner-qwen` sent 51.8%.
  The thresholds come from the calibration split, targeting 95% selective accuracy on intent+complaint.
* On the records Kev answered itself, Kev and Qwen gave identical intent labels. As a result, the hybrid's intent
  accuracy (81.8%) is statistically indistinguishable from Qwen alone (81.4%), and its decision accuracy is 96.6%
  vs 96.1%.
* It is faster than Qwen alone on combined requests (p50 1,012 vs 1,874 ms) and **slower** on classification-only
  requests (827 vs 650 ms), because more than half of the requests pay for Kev *and* Qwen.
* Extraction in the hybrids is GLiNER's (78.1–80.9% EM), which is clearly below NuExtract3 (99.8%) and Qwen (91.0%).
  On the long-input group, the hybrids' extraction exact match is identical to GLiNER2.5-base's in every cell, so
  the fallback did not change any long-document result.

## Languages (intent accuracy en / nl / fr)

| Candidate | en (n=466) | nl (n=111) | fr (n=116) |
|---|---|---|---|
| qwen3.5-4b | 82.4% | 83.2% | 77.1% |
| nuextract3 | 78.2% | 82.0% | 79.2% |
| kev-0.8b | 74.0% | 62.9% | 65.6% |
| gliner2.5-decide | 78.5% | 69.7% | 74.0% |
| gliner2.5-base | 68.2% | **46.1%** | 63.5% |
| gliner2.5-multi | 68.6% | 62.9% | 65.6% |
| gliner2.5-small | 71.7% | 37.1% | 53.1% |

(Kev/Decide n = 261/89/96.) The generative models are stable across languages. The encoders and Kev lose 4–35
points on Dutch or French. GLiNER2.5-multi narrows the Dutch gap for intent but is much weaker on
sentiment/category.

## Resources (MacBook, `ps` sampling every 500 ms, per process)

| Candidate | RSS mean / peak MB | CPU mean % (per-core sum) | Ollama loaded size | Weights on disk |
|---|---|---|---|---|
| kev-0.8b | 1,419 / 1,658 | 62 | 9.46 GiB | 1.42 GiB |
| qwen3.5-4b | 3,613 / 3,619 | 52 | 11.85 GiB | 3.70 GiB |
| nuextract3 | 8,774 / 13,021 | 283 | 3.49 GiB | 3.15 GiB |
| gliner2.5-base | 5,097 / 10,627 | 392 | – | 0.73 GiB |
| gliner2.5-small | 3,590 / 7,959 | 381 | – | 0.28 GiB |
| gliner2.5-multi | 2,042 / 4,377 | 325 | – | 1.09 GiB |
| gliner2.5-decide | 1,758 / 1,774 | 257 | – | 1.82 GiB |

The Ollama "loaded size" is what `/api/ps` reports. It includes the KV/context allocation, which is why the
1.4 GB Kev checkpoint occupies 9.46 GiB. The `ps` RSS of Ollama runner processes does not capture all Metal/GPU
memory, so read the two columns together. The GLiNER peaks come from the 2,000–5,000-token inputs. Pipelines are
not sampled separately; see their stage models. **GPU utilization and energy were not measured:** there is no
reliable unprivileged method on macOS, and nothing is estimated.

## OpenShift Developer Sandbox (GLiNER2.5-base only)

| Metric | Sandbox | (MacBook, for orientation only) |
|---|---|---|
| Intent acc / extraction EM / decision acc | 62.8% / 76.6% / 71.2% | identical |
| Latency p50 / p95 / p99 | 776 / 1,209 / 55,924 ms | 161 / 206 / 4,752 ms |
| p50 by input length 100 / 500 / 2,000 / 5,000 tok | ≈310 / 950 / 6,800 / 33,000–56,000 ms | ≈84 / 184 / 910 / 3,230–4,790 ms |
| Throughput c=1 / 4 / 8 | 1.34 / 1.26 / 1.17 req/s | 6.2 / 6.3 / 6.3 req/s |
| Peak RSS (process high-water mark) | 6.14 GiB ([pod stats](../results/runs/20261107-220443-d679/gliner-pod-stats.json)) | 10,627 MB (`ps` peak) |

The quality numbers are identical to the MacBook's, so the model and service behave deterministically across
platforms. The latencies are **not like-for-like:** the Sandbox has a shared Xeon vCPU, CPU-only torch, a
container limit of 2 CPUs, and an extra network hop through the backend service. Kev (MLX), Qwen3.5-4B and
NuExtract3 were not deployed in the Sandbox (see [openshift-sandbox.md](openshift-sandbox.md)).

## Commercial API models (opt-in comparison, 2026-11-08)

The original benchmark used local models only. On request, two "simple" and two "complex" commercial models were
added as a reference point: **GPT-6 Luna** and **GPT-6.1 Sol** (OpenAI), **Claude Haiku 5.5** and **Claude Opus 5.5**
(Anthropic). They get exactly the same input as Qwen3.5-4B: the same system prompt (`qwen-system-v1.txt`), the
same output fields, and strict JSON-Schema structured output. Same dataset, split, seed and phase parameters as
the canonical MacBook run. Each model ran at the lowest reasoning setting it accepts (GPT-6 Luna `none`, GPT-6.1
Sol `low`, Haiku thinking disabled with effort `low`, Opus effort `low` with adaptive thinking). **No call in either
run used any reasoning/thinking tokens** (checked in every raw response), so these are "thinking off" results
for all four. The model choice per tier is ours: Luna and Haiku are the two vendors' cheapest current tiers
(both $0.10 / $0.50 per 1M input/output tokens); Sol and Opus are their larger general models. OpenAI's top tier
(GPT-6 Astra) was not run.

These numbers come from a different kind of system: unknown hardware and model size, a network round trip
(median TCP connect from the client was ≈ 20 ms to both APIs), shared multi-tenant serving. Latency here is what
an application on this network would see, not a measure of model speed, and it is not comparable like-for-like
with the local models. Both runs used the same client, at the same time (the two providers ran in parallel).

### Quality (693 evaluation records, failures count as errors)

| Model | Intent acc [95% CI] | Sentiment | Category | Complaint | All 4 labels | Extraction EM [CI] | Decision acc |
|---|---|---|---|---|---|---|---|
| **Claude Opus 5.5** | **91.9%** [89.0–94.1] | 97.8% | 99.1% | 98.9% | **89.5%** | 100.0% [99.1–100.0] | 98.9% |
| GPT-6.1 Sol | 89.0% [85.8–91.6] | 96.6% | 96.6% | 97.3% | 85.7% | 100.0% [99.1–100.0] | 100.0% |
| GPT-6 Luna | 88.6% [85.3–91.2] | 97.3% | 97.5% | 97.3% | 84.8% | 99.1% [97.6–99.6] | 99.4% |
| Claude Haiku 5.5 | 85.0% [81.4–88.0] | 94.0% | 88.8% | 93.7% | 76.5% | 99.3% [97.9–99.8] | 94.9% |
| *local:* Qwen3.5-4B | 81.4% [77.5–84.7] | 94.4% | 84.1% | 94.4% | 64.6% | 91.0% [87.9–93.4] | 96.1% |
| *local:* NuExtract3 | 79.1% [75.1–82.7] | 92.8% | 65.7% | 83.4% | 55.4% | 99.8% [98.7–100.0] | 93.8% |

(Local rows from run `20261107-215748-1a28`.)

* **Intent.** All four commercial models beat the best local model (Qwen3.5-4B, 81.4%). Opus 5.5's interval does
  not overlap Qwen's or Haiku's. Luna, Sol and Opus overlap each other partly, so treat Luna vs Sol as tied.
* **Languages** (intent en / nl / fr): Opus 92.3 / 92.1 / 90.6%, Sol 91.2 / 85.4 / 86.5%, Luna 90.4 / 87.6 / 84.4%,
  Haiku 86.2 / 85.4 / 81.3%. Opus is the only model whose Dutch and French scores are as good as its English score.
* **Ambiguous inputs** (64 records, gold intent `none`): Opus abstained on 65.6%, Haiku 48.4%, Luna 45.3%, Sol 37.5%
  (local Qwen3.5-4B: 26.6%). This group accounts for most intent errors of Sol (40 of 49),
  Luna (35 of 51) and Opus (22 of 36), and for half of Haiku's (33 of 67).
* **Extraction.** Sol and Opus were perfect. Luna's 4 misses are all Dutch messages where it returned "ijs latte(s)",
  a split-word form that the benchmark's product normalizer does not map to `iced_latte` (the normalizer was not
  changed after seeing this, to avoid tuning on the evaluation set). NuExtract3 (local, 4.2B) is in the same range
  (99.8%).
* **Long inputs.** Luna, Sol and Opus got all 70 long documents right. **Claude Haiku 5.5 returned an empty
  extraction (valid JSON, every field null) for 3 of the 9 documents of 2,000 or 5,000 tokens where the order was
  in the middle** (`long-0060`, `long-0062`, `long-0080`). That is the same failure type as Qwen3.5-4B, which failed
  when the order came first.

### Speed (client-side, concurrency 1, each input seen once)

| Model | p50 all [CI] | p95 | p50 classification | p50 extraction | p50 combined | req/s at c=1 / 4 / 8 |
|---|---|---|---|---|---|---|
| Claude Haiku 5.5 | 1,002 ms [984–1,017] | 1,566 | 888 | 1,056 | 1,040 | 1.0 / 4.4 / 7.6 |
| GPT-6 Luna | 1,128 ms [1,087–1,152] | 1,510 | 890 | 1,182 | 1,311 | 1.1 / 3.6 / 8.2 |
| GPT-6.1 Sol | 1,587 ms [1,567–1,613] | 2,574 | 1,460 | 1,595 | 1,705 | 0.6 / 2.3 / 4.2 |
| Claude Opus 5.5 | 1,659 ms [1,638–1,682] | 3,495 | 1,662 | 1,556 | 1,772 | 0.4 / 2.1 / 3.7 |
| *local:* Qwen3.5-4B (M1 Max) | 1,398 ms | – | 645 | 1,587 | 1,874 | 1.5 / – / – (flat) |
| *local:* Kev 0.8B (M1 Max) | 182 ms | – | 182 | – | – | 5.6 (flat) |
| *local:* GLiNER2.5-base (M1 Max) | 161 ms | – | 160 | 76 | 194 | 6.2 (flat) |

* The hosted models scale with concurrency (≈ 7–8× at concurrency 8 for Luna and Haiku, ≈ 9× for Opus from a
  slower start), where every local model on the single MacBook stayed flat. No request was rate-limited.
* Input length barely matters for the hosted models: Luna and Sol stay within ≈ 1.1–1.3 s and 1.5–1.8 s from 100 to
  5,000 tokens, where the local models need 3–6 s for 5,000-token documents.
* For classification alone, the local specialists are still about 5× faster than the fastest hosted model (Kev 182 ms,
  GLiNER 160 ms vs Haiku 888 ms), and local Qwen3.5-4B (645 ms) is faster than every hosted model.
* No prompt-cache hits were detected (OpenAI `cached_tokens` was 0 on every call; Anthropic caching was not
  requested), so "repeat p50" is a plain repetition.

### Cost (all phases, provider-reported tokens)

Computed with `scripts/cloud-cost.py` from the token counts in every response and the providers' standard
list prices on 2026-11-08 ([OpenAI](https://developers.openai.com/api/docs/pricing),
[Anthropic](https://platform.claude.com/docs/en/about-claude/pricing)). Each model made 944 calls (warm-up,
quality, latency repeats, throughput, first call).

| Model | Price in / out per 1M | Input tokens | Output tokens | Mean in / out per call | Cost of the run | Per 1,000 calls |
|---|---|---|---|---|---|---|
| GPT-6 Luna | $0.10 / $0.50 | 565,818 | 39,480 | 599 / 42 | $0.08 | $0.08 |
| Claude Haiku 5.5 | $0.10 / $0.50 | 1,177,497 | 54,760 | 1,247 / 58 | $0.15 | $0.15 |
| GPT-6.1 Sol | $2.00 / $10.00 | 565,818 | 44,704 | 599 / 47 | $1.58 | $1.67 |
| Claude Opus 5.5 | $4.00 / $20.00 | 1,178,441 | 58,103 | 1,248 / 62 | $5.88 | $6.22 |

Total for both runs: **$7.69**. The Anthropic models report about twice as many input tokens for the same
requests. The text is identical, so the difference is tokenizer and structured-output overhead on the provider
side. The token counts are as reported, not adjusted. Local models have no per-call price, but their hardware,
power and operations cost is real and was not measured.

## Measurement problems found and fixed during this work

1. **Prompt cache.** In the first MacBook run, Kev's throughput was 300+ req/s with a 4 ms p50. Kev's
   `/v1/systemone` has no generation step, so a fully cached prompt is answered almost instantly, and the throughput
   phase was replaying inputs from the latency phase. Generative endpoints show the same effect in a milder form:
   they always re-evaluate the last few tokens, so a replayed prompt appears as "all but 1–8 tokens cached".
   Ordinary sharing of the system prompt leaves 9 or more tokens uncached.
   * **Rule:** a request is a *prompt-reuse hit* when at most 8 prompt tokens were evaluated
     (`prompt_eval_count − prompt_eval_cached_count ≤ 8`, any stage). Hits are excluded from headline and
     per-length latency and counted in the report.
   * **Phase design:** each throughput level gets fresh inputs.
   * **Warm-up:** now drawn from records *outside* the evaluated selection (the calibration split). The canonical
     run still warmed up on the last 10 evaluation records. That made 8 NuExtract3 requests on 5,000-token
     documents reuse the warm-up prompt (about 800 ms instead of about 6,100 ms), and those requests are excluded.
   * **Duplicate texts:** the remaining quality-pass hits (25 for Kev, 26 for Qwen) are explained by duplicate texts.
     28 of the 693 evaluation records repeat the text of an earlier record (19 distinct texts, all in the ambiguous
     and classification groups), a quirk of the template-based generator. Every Kev hit is such a duplicate except
     one. The duplicates also count twice in the quality metrics, which is a small bias listed under "What was not
     measured".
   * **Repeated inputs:** the "repeat p50" column (latency phase) mostly measures cache behaviour for repeated
     identical inputs. Kev answers in 4.2 ms there, with 90 of 120 hits, and Qwen in 596 ms, also with 90 of 120.
2. **OOM in the Sandbox.** With a 3 Gi limit, the GLiNER pod was OOMKilled on the long inputs. Every later
   request then failed with `Connection refused`. The report now prints a warning when more than 2% of requests
   fail with infra errors, and the limit was raised to 12 Gi (measured peak 6.14 GiB).
3. **Stale images after redeploy.** `deploy.sh` rebuilt the images, but the `:latest` Deployments did not roll.
   The script now restarts them, and the running image digests were verified against the ImageStreams.

## What was not measured

* `kev:4b-mlx-bf16`: the model did not fit on the benchmark machine's free disk (MODEL_MISSING). Only Kev 0.8B
  was benchmarked.
* `datasets/fixtures/edge-cases-v1.jsonl` (24 hand-written cases) is shipped and used in tests, but was not
  part of these runs.
* Qwen3.5-4B with thinking enabled, other quantizations, `OLLAMA_NUM_PARALLEL` > 1, GLiNER on MPS/GPU, and any
  GPU in the Sandbox (the quota is 0).
* A de-duplicated evaluation set. 28 of the 693 evaluation records (4%) repeat an earlier record's text; quality
  metrics count them as separate records. A generator fix that rejects duplicates would change the dataset hash,
  so it was left for a `coffee-v2`.
* Commercial models: OpenAI's top tier (GPT-6 Astra), Claude Sonnet 5.5, higher reasoning/effort settings, batch
  or priority tiers, other regions, and repeated runs on other days (hosted latency varies over time). Model
  versions behind the API names can change without notice; the run date is 2026-11-08.
* Statistical comparisons between candidates beyond the printed confidence intervals. Where intervals overlap
  (for example Qwen vs the hybrids on intent), treat the candidates as tied.

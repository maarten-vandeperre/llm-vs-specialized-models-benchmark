# Methodology

> All results are produced on a **synthetic, fictional** coffee-shop dataset. They show how model families behave on
> bounded business tasks under controlled conditions. They do **not** predict accuracy on real customers.

## Dataset (`coffee-v1`, seed 42)

`java -jar backend/target/quarkus-app/quarkus-run.jar generate-dataset --seed=42` writes
`datasets/evaluation/coffee-v1.jsonl`. Running it again with the same seed gives a byte-identical file;
`DatasetGeneratorTest` checks this.

| Group | Records | Task | Contents |
|---|---|---|---|
| classification | 300 | CLASSIFICATION | complaints, requests, praise and questions; explicit and implicit phrasing; negation; contradictions; typos; irrelevant text |
| extraction | 250 | EXTRACTION | order sentences with 1–3 products, quantities as digits or words, money in several formats; missing ids or totals; numeric and product distractors |
| combined | 150 | COMBINED | complaint + request + order line (+ total in 80% of records) |
| ambiguous | 96 | CLASSIFICATION | messages without a clear intent; gold = `none` / `neutral` |
| multilingual | 108 | COMBINED | 36 triples with identical facts in English, Dutch and French |
| long | 96 | EXTRACTION | an order sentence embedded in ≈100 / 500 / 2,000 / 5,000 tokens of irrelevant text, at start / middle / end |

Languages: the non-multilingual groups are 70% English and 15% each Dutch and French (ambiguous: 60/20/20).
**Splits:** 30% `calibration`, 70% `evaluation`, assigned per record (per triple for multilingual records).
Thresholds and prompts are never tuned on `evaluation`. Benchmarks use `evaluation` by default.

### Ground-truth rules

The generator first samples a latent situation (intent, complaint, products, order id, currency). It then renders
text from aligned en/nl/fr phrase banks. Gold labels come from those rules alone:

* sentiment: any complaint → `negative`; praise → `positive`; otherwise `neutral` (mixed or uncertain → `neutral`);
* category: derived from the complaint (`temperature`/`taste` → `product_quality`, `wrong_item`/`missing_item`/
  `late_delivery` → `delivery`, `overcharged` → `billing`, `rude_staff` → `service`, none → `general`);
* contradictions: the **final** stated request wins;
* total = Σ catalog price × quantity;
* decision = `DecisionPolicy.decide(intent, complaint, hasOrderId, total)`.

Labels are English identifiers for every language. A Dutch message is never scored against a translated label.
`datasets/fixtures/edge-cases-v1.jsonl` adds 24 hand-authored edge cases (sarcasm, compact formats, very short
inputs, uncertain quantities, the canonical examples from the brief).

### Known limitations of the data

* Template-generated text is less diverse than real customer messages. Models that generalize well may be
  under-rewarded, and pattern-matching may be over-rewarded.
* Approximate token counts (words × 1.33) only size the long documents. The input tokens each model actually saw
  are recorded per response.
* Some rendered sentences are slightly unnatural (e.g. a temperature complaint about a pastry). The labels are
  still unambiguous under the rules.

## Tasks per candidate

Each record is sent to each candidate once per phase. When a candidate doesn't support a record's task, the runner
does one of two things:
* for a COMBINED record, it sends the subtask the candidate does support (e.g. CLASSIFICATION for Kev);
* otherwise, it does not send the record and counts it as `incompatibleRecords`, not as an error.

Inputs that a candidate rejects at runtime (e.g. too long) are `UNSUPPORTED` outcomes. They **do** count as errors
in quality metrics.

## Phases (per candidate, in seeded-shuffled order)

1. **Contention control.** All Ollama models the candidate does not need are unloaded. Hybrid pipelines keep
   their stage models loaded together, and the report labels this as concurrent-model contention.
2. **Cold start** (optional). Ollama models are unloaded via `keep_alive: 0`. The first request is then timed
   client-side, together with Ollama's reported `load_duration`. GLiNER loads at service start-up, so its cold
   start is the service-reported `load_seconds`.
3. **Warm-up.** 10 requests on records from *outside* the evaluated selection (with `--split=evaluation` these come
   from the calibration split), persisted but never scored. This keeps a prompt cache from serving a measured
   request from a warm-up request.
4. **Quality pass.** Every compatible record once, at concurrency 1. This pass alone feeds all quality metrics
   **and the headline latency** (each input seen once).
5. **Latency repetitions.** Repetitions 2–5 re-run the first 30 compatible records. They are reported separately
   as "repeat p50" because a runtime may answer an identical input from its prompt cache.
6. **Throughput.** 40 requests at each concurrency level (1, 4, 8). Each level uses its own window of records
   (after the latency sample), so no level replays inputs from the previous phase. Requests/s = successful
   requests / wall-clock time.

### Prompt-cache hits

Ollama reports `prompt_eval_cached_count`. Kev's `/v1/systemone` has no generation step, so a fully cached prompt
is answered in about 4–20 ms, which measures a cache lookup, not inference. Generative endpoints always
re-evaluate the last few tokens, so for them a replayed prompt appears as "all but 1–8 tokens cached". Ordinary
reuse of the shared system prompt leaves 9 or more tokens uncached, which is also what happens in production.

For the optional OpenAI models the counter is `usage.prompt_tokens_details.cached_tokens`. OpenAI caches prompts of
1,024+ tokens in 128-token blocks, so a replay counts as a full hit when fewer than 128 tokens are uncached. The
Anthropic models are never sent `cache_control`, so they do not cache.

* **Rule:** a request is a **prompt-reuse hit** when at most 8 prompt tokens were evaluated, for any stage
  (`PromptCache.MAX_UNCACHED_TOKENS`).
* **Latency:** hits are excluded from the headline and per-length latency.
* **Reporting:** hits are counted per phase, and in throughput they are counted, not excluded.
* **Raw data:** nothing is deleted. The raw responses keep the counters.

Two sources of hits remain by design or by data: the latency repetitions (identical inputs, reported separately as
"repeat p50"), and 28 duplicate texts in the evaluation split (see [benchmark-results.md](benchmark-results.md)).

The first full run (`20261107-202221-3633`) used the old phase design: warm-up on the first records, throughput
replaying the latency sample. Kev's throughput there (300+ req/s) consisted entirely of cache hits. That run is
kept for transparency but superseded.

## Metrics

**Classification** (fields intent, sentiment, category, complaint): accuracy with a Wilson 95% CI, macro P/R/F1
over the label vocabulary with a bootstrap 95% CI (1,000 resamples, seed 1234), and per-label stats. **Failures
count as wrong predictions** (`__timeout`, `__malformed`, …) and are never excluded. The headline figure is the
intent field. The report also lists per-field accuracy, the mean over fields, and "all four correct".

**Extraction.** Every model's output goes through the same normalization (`ValueNormalizer`, `ProductCatalog`):
order ids are trimmed and upper-cased with a leading `#` removed; quantities are digits or en/nl/fr number words,
with a missing quantity counting as 1; money accepts `€12.50`, `12,50 €` and `12,50 euro`; currency comes from the
field or from the money symbol; products map to a canonical id across languages, plurals and articles; repeated
mentions of one product keep the maximum quantity.
* *Exact match*: order id, items (product → quantity), total and currency all correct.
* *Entity P/R/F1* over the tuples `orderId`, `total`, `currency` and `item=product×qty`.
* *Hallucination rate*: predicted values that do not occur in the input (order id not in the text, product never
  mentioned, amount not present), divided by predicted tuples.
* *Missing rate*: gold values with no prediction, divided by gold tuples.
* *Offset accuracy* (models with offsets only): the predicted span's text normalizes to the gold id and overlaps the
  gold span.

**Decision accuracy** (COMBINED tasks): `DecisionPolicy` is applied to every model's prediction, so all candidates
are comparable. `falseAutoRefunds` counts the risky errors.

**Abstention** (ambiguous group): share of predictions that chose `none` (correct abstention), share that chose a
concrete intent (false-positive decisions), and overall coverage.

**Calibration.** Computed only where native scores exist (Kev, GLiNER, hybrids), using the top-label score of the
chosen label, pooled over the four fields. Reported: Brier score, 10-bin ECE, reliability bins, accuracy vs.
coverage, and risk/coverage at 0.80. The score definitions differ by family: Kev's softmax probability vs. GLiNER's
label confidence. They are model scores, not calibrated probabilities of correctness, and are reported as such.
No probabilities are invented for generative models.

**Performance.** Client-side latency (measured in Java around the whole HTTP exchange) is kept separate from
server-side duration (Ollama `total_duration`; GLiNER service inference time). Kev's System One API reports no
durations, so its server latency is `n/a`. Reported: p50/p90/p95 with a bootstrap CI on p50, and p99 only when
n ≥ 100. Tokens/s = Σ `eval_count` / Σ `eval_duration` (Ollama generative models only). Kev's `output_tokens` are
internal scoring tokens.

**Resources.** `ps` is sampled every 500 ms for the serving processes (`ollama` server + runners; the GLiNER
process on its own port). This gives mean/peak RSS and CPU%. Ollama's `/api/ps` loaded size and the on-disk model
size are recorded too. GPU utilization and energy are **not measured**: on macOS they require root
`powermetrics`, and nothing is estimated. On Apple Silicon, Metal allocations live in unified memory and are only
partly visible in RSS; use the Ollama loaded size alongside RSS.

## Reproducibility record

Every run stores `environment.json` (OS, chip, memory, CPU count, Java, Quarkus, Ollama version, every model tag
with digest and quantization, GLiNER/torch versions, load average, `OLLAMA_NUM_PARALLEL`), `status.json` (the full
config), the dataset SHA-256, the hybrid thresholds and their source, the model order, prompt hashes (in model
notes), and every individual call with its raw provider response.

## Threats to validity

* Models run one at a time on one machine. Hybrids load two or three models together, which can slow them down
  compared with dedicated hardware.
* GLiNER runs on CPU (`GLINER_DEVICE=cpu`) for reproducibility, while Ollama uses Metal/MLX. This is a realistic
  deployment, but the hardware is not identical.
* The GLiNER service serializes inference per process (one lock). Its throughput at concurrency > 1 therefore
  shows queueing, not parallel inference.
* MacBook and OpenShift results come from different hardware and must not be compared as like-for-like.
* Small groups (e.g. 24 records per length bucket) give wide confidence intervals. Read differences together with
  the CIs.

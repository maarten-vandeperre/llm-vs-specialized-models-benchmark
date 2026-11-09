# Do We Really Need an LLM for That? Benchmarking Decision Models, Extraction Models and LLMs

*Every number in this post comes from the runs documented in [benchmark-results.md](benchmark-results.md). The
dataset is synthetic, and the code and data are in this repository, so you can re-run all of it.*

Many "AI features" in business software are bounded tasks: read a customer message, decide what the customer
wants, pull out the order number, the items and the total, and pick an action. Today the default answer is "send
it to an LLM". The question we wanted to answer with measurements instead of opinions: **for tasks like this, does
a general-purpose LLM actually beat smaller, specialized models, and what does it cost in latency and
throughput?**

## What we compared

We used one benchmark harness (Quarkus 3.40 LTS on Java 25). The core comparison runs entirely on local
open-weight models. As a reference point we then ran the same benchmark against four commercial API models: a
"simple" and a "complex" model from both OpenAI and Anthropic.

| Candidate | What it is | How it is called |
|---|---|---|
| **Kev 0.8B** (`kev:0.8b-mlx-bf16`) | a *decision model*: answers typed questions with a probability distribution, no text generation | Ollama `/v1/systemone` |
| **GLiNER2.5** base / small / multi, **GLiNER2.5-Decide** | encoder models for schema-driven span extraction and classification | official `gliner2` library behind FastAPI |
| **NuExtract3** (4.2B, Q4_K_M) | a generative model trained specifically for extraction | Ollama, template protocol |
| **Qwen3.5-4B** | a general-purpose LLM | Ollama via LangChain4j, JSON-Schema output, thinking off |
| **GPT-6 Luna / GPT-6.1 Sol** | commercial LLMs, simple / complex (OpenAI) | Chat Completions, same prompt and JSON Schema as Qwen, lowest reasoning setting |
| **Claude Haiku 5.5 / Opus 5.5** | commercial LLMs, simple / complex (Anthropic) | Messages API, same prompt and JSON Schema as Qwen, lowest reasoning setting |
| **Hybrids** | GLiNER for extraction → Kev for classification → Qwen fallback when confidence is low | backend pipeline |

The task: a fictional coffee shop's customer messages in English, Dutch and French. There are 1,000 generated
records, and we evaluated the 693 in the evaluation split. Each message gets four labels (intent, sentiment,
category, complaint), an extraction (order ID, items with quantities, total, currency) and a business decision
(auto-refund, send replacement, human review, …). Ground truth is generated alongside the text. The hybrid
routing thresholds were tuned on a separate calibration split, never on the evaluation data.

The main run used an Apple M1 Max (64 GB) with 10 warm-up requests, a full quality pass, 5 latency repetitions,
and throughput tests at concurrency 1, 4 and 8. That is 9,890 calls with zero failures. The commercial models
ran the next day with identical parameters from the same laptop: 3,776 calls, zero failures, $7.69 in total.

## Finding 1: for extraction, a specialized 4B model beats the local generalist

| | Extraction exact match | Entity F1 | Hallucinated fields |
|---|---|---|---|
| **NuExtract3** | **99.8%** [98.7–100.0] | 1.00 | 0.0% |
| Qwen3.5-4B | 91.0% [87.9–93.4] | 0.96 | 0.2% |
| GLiNER2.5-base | 76.6% [72.4–80.4] | 0.94 | 0.0% |
| *GPT-6.1 Sol / Claude Opus 5.5 (API)* | *100.0%* [99.1–100.0] | 1.00 | 0.0% |
| *GPT-6 Luna / Claude Haiku 5.5 (API)* | *99.1% / 99.3%* | 1.00 | 0.2% / 0.0% |

NuExtract3 is a model of about the same size as Qwen3.5-4B, but trained only for extraction, and it was almost
perfect on our records. The difference shows most on long inputs. When a message *started* with the order and was
followed by 2,000 or 5,000 tokens of unrelated text, **Qwen3.5-4B returned an empty extraction in 11 of 11
cases**. The JSON was valid, every field was null, and nothing was truncated. NuExtract3 got all 70 long documents in the evaluation split right, at any length and position.

The big hosted models are perfect here too, but that is the point: a 4B local extraction model is in the same
range as GPT-6.1 Sol and Claude Opus 5.5. Smaller is not automatically safer, either. Claude Haiku 5.5 showed the
same failure as Qwen, an all-null answer, on 3 of the 9 documents of 2,000+ tokens where the order sat in the *middle* of the text.

GLiNER is in a different class. It returns exact character offsets (99% of them correct) and never invented a
value, but it struggled to pair items with quantities ("two lattes and a muffin"). It is an excellent span finder
and a weaker structure builder.

## Finding 2: for nuanced classification, the LLM is still ahead

| | Intent acc [95% CI] | Sentiment | Complaint | All 4 labels right |
|---|---|---|---|---|
| **Qwen3.5-4B** | **81.4%** [77.5–84.7] | 94.4% | 94.4% | 64.6% |
| NuExtract3 | 79.1% [75.1–82.7] | 92.8% | 83.4% | 55.4% |
| GLiNER2.5-Decide | 75.8% [71.6–79.5] | 66.1% | 67.9% | 16.6% |
| Kev 0.8B | 70.0% [65.5–74.0] | 86.3% | 87.2% | 38.3% |
| GLiNER2.5-base | 62.8% [58.2–67.1] | 60.8% | 71.1% | 27.4% |

Our intents include deliberately hard cases: a customer who asks for a refund and then changes their mind, polite
complaints, ambiguous messages. The general LLM handled these best, and it was stable across languages (82% /
83% / 77% intent accuracy for en/nl/fr). The specialized classifiers lost 4–35 points on Dutch or French.
GLiNER2.5-base dropped to 46.1% intent accuracy in Dutch.

The commercial models raise the bar further. Claude Opus 5.5 reached **91.9%** intent accuracy [89.0–94.1] and got
all four labels right on 89.5% of messages (Qwen: 64.6%). It was also the only model as good in Dutch and French as
in English. Even the cheapest tiers beat the best local model: GPT-6 Luna 88.6%, Claude Haiku 5.5 85.0%. Most of
their remaining errors are on the deliberately ambiguous messages, where Opus answered "no clear intent" most often
(65.6%, against 26.6% for Qwen).

Kev 0.8B is the smallest model here, and its intent accuracy is clearly below Qwen's: the confidence intervals do
not overlap. A larger Kev (4B) exists, but we could not test it on this machine (disk space), so this verdict is
about the 0.8B model only.

## Finding 3: the specialists are 4–16× faster

| | p50 latency | Throughput (req/s) |
|---|---|---|
| GLiNER2.5-small | 85 ms | 11.8 |
| GLiNER2.5-base | 161 ms | 6.2 |
| Kev 0.8B (classification) | 182 ms | 5.6 |
| NuExtract3 | 1,359 ms | 0.8 |
| Qwen3.5-4B | 1,398 ms (classification only: 650 ms) | 1.5 |
| *Claude Haiku 5.5 (API)* | *1,002 ms (classification only: 888 ms)* | *1.0 at c=1, 7.6 at c=8* |
| *GPT-6 Luna (API)* | *1,128 ms (890 ms)* | *1.1 at c=1, 8.2 at c=8* |
| *GPT-6.1 Sol (API)* | *1,587 ms (1,460 ms)* | *0.6 at c=1, 4.2 at c=8* |
| *Claude Opus 5.5 (API)* | *1,659 ms (1,662 ms)* | *0.4 at c=1, 3.7 at c=8* |

For classification alone, Kev answers in 182 ms against Qwen's 650 ms (3.6×). For the full task, GLiNER2.5-base is
8.7× faster than Qwen, with 4× the throughput. On this machine, concurrency did not increase any single model's
throughput: requests queue, and latency grows linearly (Qwen: 646 ms at concurrency 1, 5,170 ms at concurrency 8).
Long inputs hurt everyone. GLiNER2.5-base needs about 3–5 s for a 5,000-token document, and Qwen and NuExtract3
need about 5–6 s.

The hosted models behave differently. Their latency includes a network round trip and shared serving, so it is not
comparable to local inference as a measure of model speed. But it is what an application sees: about 1 to 1.7 s,
nearly independent of input length (5,000 tokens cost Luna no more than 100), and throughput that scales with
concurrency instead of queueing. For classification, the local specialists are still about 5× faster than the fastest
hosted model, and a local 4B LLM on a laptop (Qwen, 645 ms) beat all of them.

We also ran GLiNER2.5-base on a Red Hat OpenShift Developer Sandbox, which has no GPU and a shared CPU. Quality
was identical to the MacBook, and the median latency was 776 ms. That shows CPU-only deployment works for an
encoder model. It also shows that "small" models still need care: the pod reached a 6.1 GiB memory peak on the
longest inputs, and our first deployment with a 3 GiB limit was OOMKilled halfway through the run.

## Finding 4: native confidence is the specialists' secret weapon, if it is honest

Kev and GLiNER return probabilities. Qwen and NuExtract3 return none, and we did not invent any. Probabilities
make routing possible: automate when the model is sure, escalate when it is not. But the scores have to mean
something.

* **Kev 0.8B is conservative.** Among labels scored at least 0.80, only 3.2% were wrong, but only 16% of labels
  reached that score.
* **GLiNER2.5-base is overconfident.** Among labels scored at least 0.80, 24.1% were wrong.

A raw score is not a calibrated probability. If you build automation on one, calibrate it on held-out data first.

## Finding 5: hybrids buy speed only when the fallback is rare

The best-scoring pipeline (GLiNER → Kev → Qwen fallback) matched Qwen on decisions: 96.6% vs 96.1% decision
accuracy, and 81.8% vs 81.4% intent accuracy, which is statistically tied. It was faster on combined requests
(1,012 vs 1,874 ms median). But to reach 95% selective accuracy, the calibrated threshold sent **55.6% of
messages to Qwen anyway**. On classification-only requests the hybrid was therefore *slower* than calling Qwen
directly (827 vs 650 ms), because most requests paid for Kev *and* Qwen. Its extraction was GLiNER's: 78.1%
exact match, far below NuExtract3.

A hybrid is an economic bet on your traffic. It pays off when most messages are easy for the small model.

## Finding 6: the "just call an API" option is cheap, accurate, and not fast

| Commercial model | Intent acc | Extraction EM | p50 | Cost per 1,000 calls |
|---|---|---|---|---|
| GPT-6 Luna | 88.6% | 99.1% | 1,128 ms | $0.08 |
| Claude Haiku 5.5 | 85.0% | 99.3% | 1,002 ms | $0.15 |
| GPT-6.1 Sol | 89.0% | 100.0% | 1,587 ms | $1.67 |
| Claude Opus 5.5 | **91.9%** | 100.0% | 1,659 ms | $6.22 |

Costs come from the token counts each provider reported, at the list prices on the day of the run. With these
short prompts, the cheap tiers cost cents per thousand messages and beat every local model we ran on
classification. The expensive tiers buy a few more points of intent accuracy at 20–80× the price. None of them used any
reasoning tokens at these settings.

What the API does not buy you: latency below about 0.9 s, native confidence scores (none of the four returns label
probabilities), control over when the model behind the name changes, and keeping customer text inside your own
infrastructure. Those are often the real reasons to run a model yourself, and this benchmark cannot weigh them for
you.

### When is running it yourself actually cheaper?

The API bill above is small for two reasons. Our run was small (944 calls per model), but more importantly every
call is small: about 600 input tokens and 40–60 output tokens of JSON, with no reasoning tokens. The number that
scales is the cost per 1,000 calls, and at $0.08 for GPT-6 Luna, that is hard to beat.

Bigger requests do not tip the balance automatically. API cost grows linearly with input tokens: at about 5,000
input tokens per call, Luna costs about $0.52 per 1,000 calls, Sol about $10.50 and Opus about $21. But local
models pay for long inputs too, in time instead of money: 3–6 s per 5,000-token document on our laptop against
well under a second for short messages, so each machine handles fewer requests. A local model has no per-call
price. Its cost is the machine, and that machine costs the same whether it is busy or idle. **The break-even point
is driven by sustained volume more than by request size.**

To make that concrete, here is an *illustration, not a measurement*: assume a server at $500 a month all-in
(hardware, power, some operations time), running flat out at the throughput we measured on the MacBook.

| Local model | Throughput | Max calls / month | Local cost per 1,000 calls |
|---|---|---|---|
| GLiNER2.5-base | 6.2 req/s | ~16M | ~$0.03 |
| Kev 0.8B | 5.6 req/s | ~14M | ~$0.035 |
| Qwen3.5-4B | 1.5 req/s | ~3.9M | ~$0.13 |

Against Claude Opus 5.5 ($6.22 per 1,000), that server pays for itself at about 80,000 calls a month, and a busy
one is 50–200× cheaper. Against GPT-6.1 Sol it breaks even at about 300,000 calls a month. Against GPT-6 Luna, the
small specialists are only 2–3× cheaper, and only near full utilization (about 6M calls a month), while a local
Qwen3.5-4B is *more* expensive per call than Luna. Proper GPU serving with batching would likely do much better
than our laptop's flat throughput, but we did not measure that.

So running a model yourself is *way* cheaper when:

* the task is narrow and repetitive (intent, sentiment, entity extraction) and a small specialist is accurate
  enough for it,
* volume is high and steady, or the work is batch jobs you can schedule to keep machines busy,
* inputs are long and volume is high, so per-token pricing adds up while your hardware cost stays fixed,
* the alternative is a mid-tier or large hosted model, not the cheapest tier, or
* you already own the hardware.

It is not cheaper when traffic is low or bursty (an idle server still costs money, an idle API costs nothing),
when the comparison is against the cheapest tiers (where engineering time easily exceeds the API bill), or when
errors are expensive: an 81% vs 92% intent accuracy gap can cost more in misrouted messages than the savings are
worth. Prompt caching would also lower the API side for long, repeated system prompts. To get real numbers for
your case, measure the monthly cost of your target hardware and its sustained requests per second at your typical
input size, and compare that with the cost per 1,000 calls.

## The benchmarking traps we fell into (so you do not have to)

Our first full run reported **over 300 requests per second for Kev**. That was wrong. Kev's endpoint has no
generation step, so when Ollama finds the *whole* prompt in its cache, it answers in about 4 ms. Our throughput
phase was replaying inputs it had just seen, so we were measuring a cache lookup. Generative models show the same
effect more subtly. When NuExtract3 saw a warm-up document again, it answered in about 800 ms instead of about
6 s. We now:

* draw warm-up inputs from outside the evaluated set,
* give every throughput level fresh inputs,
* detect prompt reuse from the runtime's own counters and exclude it from latency, and
* report repeated-input latency separately.

Three more lessons:
* Our "14 errors" in the Sandbox were really a pod that died of OOM.
* Our redeploy script rebuilt the images but never rolled the pods, because the Deployments pointed at `:latest`.
* Even on a synthetic dataset, a template generator can produce duplicate texts (28 of 693 here).

Check your harness as carefully as the models.

## So, do we need an LLM for that?

On this benchmark:

* **Extraction:** no. A specialized 4B extraction model (NuExtract3) was more accurate than the local general LLM,
  robust to long inputs where that LLM failed, and in the same range as the largest commercial models.
* **Nuanced, multilingual intent classification:** a general LLM still earned its place, and a large one most of
  all. The specialized classifiers we could run were 6–21 points below local Qwen, and Qwen was 10 points below
  Claude Opus 5.5. If you can send the text to an API, even the cheapest commercial tier beat every local model here.
* **Volume or latency budgets:** encoders and decision models are 4–16× faster, and some of them give you
  confidence scores. For high-volume, mostly simple traffic, they can be the right primary model with an LLM
  fallback, as long as the fallback rate stays low enough to pay off.

The honest answer is "it depends", but now it depends on numbers you can reproduce.

## Caveats

* The data is synthetic. Real customer messages are messier, and your results will differ.
* All local models ran on one machine (M1 Max) and one Sandbox node. The two are not comparable like-for-like.
  The commercial models ran on unknown hardware behind a network, measured on one day (2026-11-08). Their
  latency varies over time, and the model behind an API name can change.
* Only Kev 0.8B was tested. Qwen3.5-4B ran with thinking disabled, and other quantizations, thinking modes and
  `OLLAMA_NUM_PARALLEL` were not explored.
* Each configuration was measured in one full run. Run-to-run variation in median latency was up to 7.4%.
* GPU utilization and energy were not measured, and nothing was estimated.
* The local cost figures in "When is running it yourself actually cheaper?" assume a $500/month server and
  full utilization. They show how the trade-off works and are not measured costs.

## Reproduce it

```bash
scripts/check-deps.sh && scripts/pull-models.sh minimal && scripts/setup-python.sh
scripts/start-gliner.sh all && scripts/calibrate.sh && scripts/full-benchmark.sh
```

See [macbook-setup.md](macbook-setup.md), [openshift-sandbox.md](openshift-sandbox.md),
[methodology.md](methodology.md) and [benchmark-results.md](benchmark-results.md).

## Sources

* Ollama decision-model support and the `/v1/systemone` API: <https://ollama.com/blog/ollama-now-supports-jev-style-decision-models>, <https://ollama.com/library/kev>
* GLiNER2: <https://github.com/fastino-ai/GLiNER2>, <https://pypi.org/project/gliner2>
* NuExtract3: <https://huggingface.co/numind/NuExtract3>, <https://huggingface.co/numind/NuExtract3-GGUF>
* Commercial API prices: <https://developers.openai.com/api/docs/pricing>,
  <https://platform.claude.com/docs/en/about-claude/pricing> (read 2026-11-08)
* Qwen3.5-4B GGUF builds: <https://huggingface.co/unsloth/Qwen3.5-4B-GGUF>. The benchmark used the Ollama
  library tag `qwen3.5:4b` (digest `7f3251fa…`).

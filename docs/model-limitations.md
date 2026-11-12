# Model limitations and protocol notes

Everything below was verified on 2026-11-07, against the Ollama registry manifests, the model templates and the
installed libraries, unless marked as vendor-reported.

## Kev (`kev:0.8b-mlx-bf16`, optional `kev:4b-mlx-bf16`)

* **Protocol:** Ollama `POST /v1/systemone`. The request carries a `state`, up to 64 typed `questions` (`choice`,
  `noul`, `score`) and `keep_alive`. It has no options object, so temperature, seed and context are not tunable.
  The response contains `answers` with `choice`, `probabilities` and `confidence`, plus `usage.input_tokens` and
  `usage.output_tokens`. **It returns no durations**, so only client-side latency exists for Kev.
* **Runtime:** requires Ollama ≥ 0.40.4 and the `decision` capability. The `-mlx-` tags use the Apple Silicon MLX
  runner, so they **do not run on Linux or OpenShift**. The library page also lists non-MLX tags
  (`kev:0.8b-q8_0`, `kev:4b-q4_K_M`, …), but this project has not verified them on Linux CPU.
* **Cannot generate strings.** Kev only selects among provided options. The `kev-0.8b` candidate is therefore
  classification-only. Extraction is a separately labelled experiment (`kev-0.8b-candsel`): a regex stage proposes
  order-id and amount candidates, Kev chooses one (or `none`), and products are scored as closed-set
  quantity questions (`0`…`6`) over the 12-item catalog. That candidate's accuracy reflects the regex stage plus
  Kev, not Kev alone.
* **`confidence` is not a probability of correctness.** It equals 1 − H(p)/ln N (distribution concentration), and
  the vendor says so too. Calibration uses `probabilities[choice]` instead.
* **Abstention:** Kev has no abstain field, so every choice question includes an explicit `none` option.
* **Vendor-reported:** Kev is English-focused, and accuracy can drop on long inputs and with reordered options.
* **Not benchmarked here:** `kev:4b-mlx-bf16` (8.4 GB) did not fit the free disk of the benchmark machine. It is
  reported as `MODEL_MISSING`, not as a result.

## Gemma 4 12B as a decision model (`gemma4:12b`, candidate `gemma4-12b-decision`)

Verified on 2026-11-10.

* **Why not a native Gemma decision model:** the only Gemma-based decision model in the Ollama registry is the
  community `aminroudaki/decisio-gemma` (Gemma 4 12B, Q4_K_M). Ollama 0.40.15 rejects it on every
  `/v1/systemone` call with *"was downloaded in a format that is no longer supported"*, even directly after a
  fresh pull (its page targets Ollama 0.35.1). Its own description of the method is: unmodified Gemma 4 12B, the
  system prompt "Answer with the letter only", and a softmax over the option letters. The benchmark therefore
  applies **that same method itself** to the official `gemma4:12b` weights (`LetterDecisionModel`).
* **Protocol:** Ollama `POST /api/chat`, **one call per label field** (four per message), `num_predict=1`,
  `temperature=0`, `seed=42`, `think=false`, `num_ctx=16384`, `truncate=false`, `logprobs=true`,
  `top_logprobs=20`. The options are lettered A, B, C, … with the same option descriptions and questions Kev
  receives, including an explicit `none` option. The customer message comes first in the prompt, so the four
  calls of a message share a prompt prefix.
* **Probabilities** are the softmax over the option letters' log-probabilities of that one token: the model's
  own token probabilities, not numbers it writes. A letter that is not among the top 20 tokens gets probability 0.
  `confidence` is 1 − H(p)/ln N, as for Kev. No answer without an option letter in the top 20 was observed; such
  an answer would count as `MALFORMED`.
* **Not the same build everywhere:** on the MacBook Ollama serves `gemma4:12b` as an MLX `nvfp4` build (digest
  c7597fc9, 7.7 GB); in the OpenShift Sandbox (Linux, CPU) the same tag resolves to a GGUF `Q4_K_M` build
  (digest 6114515d, 8.0 GB).
* **Cost of the method:** four forward passes per message instead of one. Prefix caching across the four calls
  softens this but does not remove it.
* Classification only, like Kev.

## GLiNER2.5 (`fastino/gliner2.5-base-v1`, small, multi; `fastino/GLiNER2.5-Decide`)

* **Library:** official `gliner2==2.1.0`, loaded with `AutoExtractor.from_pretrained` (boundary architecture for
  2.5-base/small/multi). `GLiNER2.5-Decide` loads as a **span** checkpoint (`architecture: span`, max 512 tokens),
  is classification-only, and is a different checkpoint from GLiNER2.5-base.
* **Outputs:** entity spans with character offsets and per-entity confidence. Classification returns only the
  chosen label and its confidence, with **no full distribution**.
* **Input length:** base/small/multi have `max_len` = 4096 tokens, and Decide has 512. Longer inputs are never
  truncated. Entity-only requests use the library's official chunking (`extract_entities_long`), and requests that
  include classification return HTTP 413 → `UNSUPPORTED`.
* **Line items:** GLiNER extracts product and quantity spans independently. The adapter pairs a quantity with the
  adjacent product (preceding first, then following, within 4 characters). This is deterministic post-processing,
  documented and included in latency.
* **Language:** base and small are English checkpoints. Only `gliner2.5-multi` (mDeBERTa) claims multilingual
  support.
* **Concurrency:** the service serializes inference per process. Run more processes for parallelism.

## NuExtract3 (`numind/nuextract3:q4_k_m`)

* **Protocol** (taken from the model's Ollama template): the JSON template goes in a message with role `template`,
  optional guidance in role `instructions`, and the document in `user` messages. When `think` is false the template
  emits an empty `<think></think>`, and thinking is **disabled** in this benchmark.
* **Types:** `verbatim-string`, `string`, `integer`, `number`, and enums as JSON arrays. Classification uses enum
  fields.
* The registry default is `num_ctx` 131072 and `temperature` 0.2. The benchmark overrides these to `num_ctx`
  16384, `temperature` 0, seed 42 and `truncate: false`.
* No label probabilities, so none are reported.

## Qwen3.5-4B (`qwen3.5:4b`)

* Accessed through LangChain4j `OllamaChatModel` with a JSON-Schema `ResponseFormat` (Ollama `format`,
  grammar-constrained), `think(false)`, temperature 0, seed 42, `num_ctx` 16384, `truncate(false)`.
* The registry defaults (`temperature` 1, `top_k` 20, `top_p` 0.95, `presence_penalty` 1.5) are overridden by the
  values above.
* System prompt: `backend/src/main/resources/prompts/qwen-system-v1.txt`. Its hash is recorded in the model notes.
* No label probabilities, so none are reported.

## Commercial API models (opt-in): GPT-6 Luna, GPT-6.1 Sol, Claude Haiku 5.5, Claude Opus 5.5

* Adapter: `infrastructure/cloud/CloudLlmModel.java`, raw HTTP through the same JDK client as the other adapters
  (no vendor SDK), so client-side latency is measured identically for both providers.
* Same system prompt (`qwen-system-v1.txt`) and the same output fields as Qwen3.5-4B, enforced with each provider's
  strict JSON-Schema structured output (OpenAI `response_format.json_schema` with `strict: true`, Anthropic
  `output_config.format`).
* Reasoning: each model runs at the lowest setting it accepts. GPT-6 Luna `reasoning_effort=none`; GPT-6.1 Sol
  `reasoning_effort=low` (`none` is rejected); Claude Haiku 5.5 thinking disabled, effort `low`; Claude Opus 5.5
  effort `low` with adaptive thinking (Opus 5.5 rejects disabled thinking). Opus can therefore think a little,
  and its output token count includes that.
* No temperature or seed is sent (the reasoning models reject non-default sampling), so outputs are not
  guaranteed to be deterministic.
* Model size, quantization and serving hardware are not disclosed. Latency includes the network round trip from
  the client machine to the provider and the provider's queueing, so it is not comparable to local inference
  as a measure of model speed, only as a measure of what an application would experience.
* Throughput measures the provider's per-account behaviour at that moment, not a hardware limit. Rate limits
  (HTTP 429) are recorded as infrastructure errors; there are no retries.
* Prompt cache: OpenAI caches prompts of 1,024 or more tokens automatically (`cached_tokens`); replays with fewer
  than 128 uncached tokens count as full hits and are excluded from headline latency. Anthropic only caches with
  `cache_control`, which is never sent.
* No label probabilities are returned, so none are reported.
* Keys come only from `OPENAI_API_KEY` / `ANTHROPIC_API_KEY`. They are sent as request headers and never written to
  logs or results. Without a key the candidate is `DISABLED`, and implicit selections ("all healthy candidates")
  never include these models.

## Hybrid pipelines

* Routing uses min(p(intent), p(complaint)) of the first-stage classifier. The thresholds come from the
  calibration split, targeting 95% selective accuracy on intent+complaint.
* When the first stage routes most requests to the LLM, the pipeline costs roughly LLM latency plus the overhead
  of the first stage. The report shows the observed fallback rate.

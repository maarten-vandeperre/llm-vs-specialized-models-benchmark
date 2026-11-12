# Architecture

## Overview

```
                    ┌───────────────────────────── React dashboard (Vite, Tailwind, Recharts) ──┐
                    │  Playground · Runner · Results · Inspect                                  │
                    └───────────────────────────────┬───────────────────────────────────────────┘
                                                    │ /api (JSON)
┌───────────────────────────────────────────────────▼───────────────────────────────────────────┐
│ Quarkus 3.40 LTS backend (Java 25)                                                            │
│                                                                                               │
│  api/            REST resources (models, datasets, inference, benchmarks, reports)            │
│  cli/            headless commands: benchmark, generate-dataset, calibrate, health, env, report│
│  application/    BenchmarkModel port, ModelInfo, ModelRegistry, ModelFactory (composition root)│
│  domain/         TaskType, BenchmarkRequest/Response, Outcome, Prediction, LabelSchema,       │
│                  ProductCatalog, ValueNormalizer, DecisionPolicy  (framework-free)            │
│  dataset/        deterministic generator + JSONL repository                                   │
│  evaluation/     Evaluator (per record) + Metrics (pure functions)                            │
│  benchmark/      BenchmarkRunner, ReportBuilder, MarkdownReport                               │
│  infrastructure/                                                                              │
│    kev/          Kev  → POST /v1/systemone        (dedicated HTTP adapter, typed questions)   │
│    letterdecision/ Gemma 4 12B → POST /api/chat   (letter log-probabilities, 1 call/label)    │
│    nuextract/    NuExtract3 → POST /api/chat      (template-role protocol, dedicated adapter) │
│    qwen/         Qwen3.5 → LangChain4j ChatModel  (JSON-Schema structured output)             │
│    gliner/       GLiNER2.5 → Python FastAPI service                                           │
│    hybrid/       pipelines exposed as ordinary candidates                                     │
│    ollama/       admin API (version, tags/digests, ps, unload)                                │
│    persistence/  results/runs/<runId>/ (JSONL, CSV, JSON, Markdown)                           │
│    system/       environment capture, process RSS/CPU sampler                                 │
└──────────┬──────────────────────────────────┬─────────────────────────────────────────────────┘
           │                                  │
   ┌───────▼────────┐                ┌────────▼───────────────────────────────┐
   │ Ollama 0.40.x  │                │ GLiNER service (Python 3.12, FastAPI,  │
   │ (native, MLX / │                │ official gliner2 2.1.0, torch CPU/MPS) │
   │ llama.cpp)     │                │ one process per checkpoint             │
   └────────────────┘                └────────────────────────────────────────┘
```

## The common model port

```java
public interface BenchmarkModel {
    String id();
    ModelInfo info();
    ModelCapabilities capabilities();   // features + accepted tasks + max input tokens
    HealthStatus health();              // UP / MODEL_MISSING / RUNTIME_UNSUPPORTED / DOWN / DISABLED
    BenchmarkResponse execute(BenchmarkRequest request);   // never throws
    default boolean unload() { ... }    // for cold-start measurement
    default List<String> runtimeModels() { ... }           // contention control
}
```

`BenchmarkResponse.outcome` distinguishes `SUCCESS`, `ABSTAINED`, `UNSUPPORTED`, `MALFORMED`, `TIMEOUT` and
`INFRA_ERROR`. Successful outputs are normalized into a `Prediction` (labels + extraction) while the raw provider
payload is always kept in `rawResponse`. Probabilities are only filled when the provider returns them:

| Field | Kev | GLiNER | NuExtract3 / Qwen |
|---|---|---|---|
| `probability` (chosen label) | `probabilities[choice]` | label `confidence` | `null` |
| `distribution` | full distribution | `null` (library returns only the top label) | `null` |
| `providerConfidence` | Kev `confidence` = 1 − H(p)/ln N (concentration, *not* correctness) | `null` | `null` |

## Why dedicated adapters instead of one LangChain4j ChatModel

* **Kev** speaks Ollama's typed decision protocol (`/v1/systemone`): one `state`, up to 64 typed questions
  (`choice`, `noul`, `score`), and a probability distribution per question. It is not a chat API and has no
  `messages`, so wrapping it in a `ChatModel` would be incorrect.
* **NuExtract3** uses custom chat roles defined in its Ollama template (`template`, `instructions`,
  `examples.input/output`, `previous_output`). LangChain4j's message types cannot express these roles.
* **Qwen3.5** is a general chat model, so it uses LangChain4j's `ChatModel` with a JSON-Schema `ResponseFormat`
  (grammar-constrained decoding in Ollama). The upstream `OllamaChatModel` (version-managed by the Quarkus
  LangChain4j BOM) is used programmatically because it supports `think(false)` and `truncate(false)`. The Quarkus
  1.13 Ollama client in this platform release has no `think` field. A small recording `HttpClient` keeps Ollama's
  `total_duration` / `eval_duration`, which LangChain4j's response mapping drops.
* **GLiNER** is a PyTorch encoder that only runs in Python, so a minimal FastAPI service wraps the official library.
  Models load at start-up and never in the request path. Each checkpoint can run in its own process (own port), so
  memory and CPU can be attributed per model.

## Hybrid pipelines

| Candidate | Flow |
|---|---|
| `hybrid-gliner-kev` | GLiNER extraction → Kev classification → `DecisionPolicy` |
| `hybrid-gliner-kev-qwen` | as above; if Kev's p(intent) or p(complaint) < threshold, or a stage fails → Qwen classifies |
| `hybrid-gliner-qwen` | one GLiNER multi-task call; if p(intent) or p(complaint) < threshold, or the input is unsupported → Qwen does the whole request |

Thresholds come from `results/calibration/thresholds.json`, which `calibrate` computes on the calibration split.
The reported latency of a pipeline is end-to-end wall-clock time, including every stage, the orchestration, and
fallback calls. The per-stage times are stored in `timing.stageNanos`.

## Design decisions

* **Clean architecture without ceremony.** The domain classes are plain Java records with no framework
  dependencies. Adapters are plain classes, and only `ModelFactory` knows about configuration.
* **File-based persistence.** Each run is a directory containing JSONL, CSV, JSON and Markdown files, so no
  database is needed. That makes runs easy to diff, archive and load into pandas.
* **The benchmark harness is in Java.** Python is used only for GLiNER's native inference.
* **Deterministic data.** The generator uses a single seeded `Random`. Multilingual triples use a seeded
  sub-stream and noise-free rendering, so all three languages contain identical facts.

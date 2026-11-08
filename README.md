# Beyond the LLM: benchmarking decision models, extraction models and LLMs

A reproducible, open-source benchmark platform that compares **specialized open-weight models** with a
**general-purpose LLM** on bounded business tasks: classification, information extraction, and both combined.
The core benchmark runs on local or self-hosted inference only. Four commercial API models (OpenAI and
Anthropic) are available as an opt-in comparison point; they never run unless you set an API key and name them.

| Candidate | Family | Runtime | Protocol |
|---|---|---|---|
| `kev-0.8b` (`kev:0.8b-mlx-bf16`) | decision model | Ollama ≥ 0.40.4 (MLX) | `/v1/systemone` typed questions + probability distributions |
| `kev-0.8b-candsel` | decision model + regex candidates | Ollama | candidate-selection extraction experiment (separately labelled) |
| `gliner2.5-base` / `-small` / `-multi` | schema-driven span extractor + classifier (encoder) | Python FastAPI + official `gliner2` | entities with offsets + label confidences |
| `gliner2.5-decide` (`fastino/GLiNER2.5-Decide`) | encoder decision/classification model | Python FastAPI | classification only (optional fifth model) |
| `nuextract3` (`numind/nuextract3:q4_k_m`) | generative extraction LLM | Ollama | template-role protocol, thinking off |
| `qwen3.5-4b` (`qwen3.5:4b`) | general-purpose LLM | Ollama via LangChain4j | JSON-Schema structured output, thinking off |
| `openai-gpt-6-luna` / `openai-gpt-6.1-sol` | commercial LLM, simple / complex (opt-in) | OpenAI Chat Completions | same prompt and fields as Qwen, strict JSON Schema, lowest reasoning setting |
| `claude-haiku-5.5` / `claude-opus-5.5` | commercial LLM, simple / complex (opt-in) | Anthropic Messages | same prompt and fields as Qwen, strict JSON Schema, lowest reasoning setting |
| `hybrid-gliner-kev[-qwen]`, `hybrid-gliner-qwen` | pipelines | backend | GLiNER → Kev → decision → optional LLM fallback |

> **Synthetic data.** The dataset is a fictional coffee shop, deterministically generated (seed 42, 1,000 records
> in English, Dutch and French). Results describe behaviour on this synthetic benchmark only.

## Quick start (MacBook, Apple Silicon)

```bash
cd benchmark-platform
scripts/check-deps.sh                 # environment detection
scripts/pull-models.sh minimal        # Kev 0.8B, Qwen3.5-4B, NuExtract3 (~9 GB); requires Ollama >= 0.40.4
scripts/setup-python.sh               # GLiNER service venv + tests
scripts/start-gliner.sh minimal       # GLiNER2.5-base on :8090   (or: all)
scripts/health.sh                     # every endpoint and candidate
scripts/smoke-benchmark.sh            # small real benchmark, headless
scripts/start-backend.sh &            # REST API on :8080
scripts/start-frontend.sh             # dashboard on :5173
```

Full details: [docs/macbook-setup.md](docs/macbook-setup.md). OpenShift: [docs/openshift-sandbox.md](docs/openshift-sandbox.md).

## Documentation

* [Architecture](docs/architecture.md): components, the `BenchmarkModel` port, adapters, hybrid pipelines
* [Methodology](docs/methodology.md): dataset, ground-truth rules, phases, metrics, threats to validity
* [Model limitations](docs/model-limitations.md): protocol facts and what each model cannot do
* [Benchmark results](docs/benchmark-results.md): measured results of the runs executed for this repository
* [Blog post](docs/blog-post.md): "Do We Really Need an LLM for That?"

## REST API

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/models` | candidates with capabilities |
| GET | `/api/models/{id}/health` | runtime/model availability |
| GET | `/api/datasets` | datasets with sizes, splits, SHA-256 |
| GET | `/api/datasets/{name}/records` | browse records (`group`, `split`, `limit`) |
| POST | `/api/inference` | `{modelId, task, text, language}` → normalized prediction + raw response |
| POST | `/api/benchmarks` | start a run (models, dataset, groups, split, limit, warmup, repetitions, concurrency, …) |
| GET | `/api/benchmarks` | list runs |
| GET | `/api/benchmarks/{runId}` | status and progress |
| GET | `/api/benchmarks/{runId}/results` | individual predictions (filters: modelId, group, outcome, phase, incorrect) |
| GET | `/api/benchmarks/{runId}/results.csv` | CSV export |
| GET | `/api/benchmarks/{runId}/report` | aggregated report (`?format=md` for Markdown) |

OpenAPI/Swagger UI: `http://localhost:8080/q/swagger-ui`.

## CLI (no frontend needed)

```bash
java -jar backend/target/quarkus-app/quarkus-run.jar benchmark --models=kev-0.8b,gliner2.5-base --groups=classification --limit=50
java -jar backend/target/quarkus-app/quarkus-run.jar generate-dataset --seed=42
java -jar backend/target/quarkus-app/quarkus-run.jar calibrate
java -jar backend/target/quarkus-app/quarkus-run.jar health | env | report <runId>
```

## Repository layout

```
benchmark-platform/
  backend/            Quarkus 3.40 LTS, Java 25 (domain, application, infrastructure, benchmark, evaluation, api, cli)
  inference/gliner/   FastAPI service around the official gliner2 library (+ tests, Containerfile)
  frontend/           React + Vite + Tailwind + Recharts dashboard (+ Containerfile)
  datasets/           evaluation/coffee-v1.jsonl (generated), fixtures/edge-cases-v1.jsonl (hand-authored)
  deployments/        podman/compose.yaml, openshift/ (kustomize, BuildConfigs, optional KServe & Ollama)
  scripts/            setup, start, health, smoke/full benchmark, calibration
  results/            runs/<runId>/ (JSONL, CSV, JSON, Markdown), calibration/
  docs/
```

## Tested versions

Java 25.0.2 · Quarkus 3.40.1.1 (LTS) · Quarkus LangChain4j 1.13.3 / LangChain4j 1.19.3 · Ollama 0.40.15 ·
Python 3.12 · gliner2 2.1.0 · torch 2.15.0 · transformers 5.22.2 · FastAPI 0.162.2 · React 19.3 · Vite 8.3 ·
Tailwind 4.3 · Recharts 3.10.

## License

Apache-2.0 for the code in this repository. Each model keeps its own licence: Kev, GLiNER2.5 and Qwen3.5 are
Apache-2.0, and NuExtract3's licence ships in its Ollama manifest.

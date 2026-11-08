# MacBook setup (Profile A)

Verified on: Apple M1 Max, 64 GB unified memory, macOS 26.6.2, OpenJDK 25.0.2, Maven 3.9.6, Ollama 0.40.15,
Python 3.12 (via `uv`), Node 24.1, Podman 5 (optional).

## 0. Check the machine

```bash
cd benchmark-platform
scripts/check-deps.sh
```

The script prints the macOS version, chip, unified memory, CPU count, Java/Maven/Python/Node/Podman versions, free
disk, the Ollama server version, the installed model tags with digests, and whether MLX is supported.

## 1. Ollama ≥ 0.40.4 (native)

Kev needs Ollama **0.40.4 or later**. Older versions reject the pull with HTTP 412 ("requires a newer version of
Ollama"), which happened during this project with 0.20.2. Update Ollama.app, or install it with
`brew install --cask ollama-app`, then start it (the app, or `ollama serve`).

Ollama must run **natively** on the Mac. Kev's `-mlx-` tags need the Apple Silicon MLX runner, and Metal is not
available inside Podman's Linux VM.

## 2. Models

| Profile | Models | Disk |
|---|---|---|
| minimal (default) | `kev:0.8b-mlx-bf16`, `qwen3.5:4b`, `numind/nuextract3:q4_k_m` + GLiNER2.5-base | ≈ 9.7 GB |
| large | minimal + `kev:4b-mlx-bf16` + GLiNER2.5 small/multi/Decide | ≈ 21 GB |

```bash
scripts/pull-models.sh minimal     # or: scripts/pull-models.sh large
```

The script checks free disk before it downloads anything. Nothing large is pulled automatically.

## 3. GLiNER service (Python)

```bash
scripts/setup-python.sh            # .venv with gliner2[local]==2.1.0, fastapi, uvicorn; runs the Python tests
scripts/start-gliner.sh minimal    # fastino/gliner2.5-base-v1 on :8090
scripts/start-gliner.sh all        # base :8090, multi :8091, GLiNER2.5-Decide :8092, small :8093
```

Each checkpoint runs in **its own process** so RSS and CPU can be attributed per model. Weights download to the
Hugging Face cache on the first start. That download happens outside every measurement, because loading happens at
service start-up. `GLINER_DEVICE=mps` enables Apple GPU acceleration; the benchmark default is `cpu` for
reproducibility. Logs go to `results/logs/gliner-<port>.log`. To stop the services: `scripts/stop-gliner.sh`.

## 4. Backend and dashboard

```bash
scripts/start-backend.sh           # mvn package + java -jar → http://localhost:8080 (Swagger UI at /q/swagger-ui)
scripts/start-frontend.sh          # Vite dev server → http://localhost:5173 (proxies /api to :8080)
scripts/health.sh                  # all endpoints + every candidate's health
```

If your `~/.m2/settings.xml` activates a profile that sets `quarkus.platform.*` properties (common on Red Hat
machines), this build is unaffected: the POM uses its own `bench.quarkus.*` properties.

## 5. Run benchmarks (headless, no frontend needed)

```bash
java -jar backend/target/quarkus-app/quarkus-run.jar generate-dataset --seed=42   # already committed; regenerates identically
scripts/calibrate.sh               # hybrid thresholds from the calibration split → results/calibration/thresholds.json
scripts/smoke-benchmark.sh         # 30 stratified records, a few minutes
scripts/full-benchmark.sh          # full methodology on the evaluation split (≈1.5–2 h on an M1 Max)
```

CLI options (all optional): `--models=a,b`, `--dataset=coffee-v1|edge-cases-v1`, `--groups=classification,long`,
`--split=evaluation`, `--languages=en,nl`, `--limit=N`, `--warmup=10`, `--repetitions=5`, `--latency-sample=30`,
`--concurrency=1,4,8`, `--throughput-requests=40`, `--timeout=180`, `--cold-start=true`, `--seed=42`, `--label=x`.

Results land in `results/runs/<runId>/` (`report.md`, `report.json`, `predictions.jsonl`, `predictions.csv`,
`environment.json`, `model-runs.json`, `status.json`). To rebuild the report of an existing run:
`java -jar backend/target/quarkus-app/quarkus-run.jar report <runId>`.

### Optional: commercial API models

```bash
export OPENAI_API_KEY=...        # never commit keys; they are only read from the environment
export ANTHROPIC_API_KEY=...
java -jar backend/target/quarkus-app/quarkus-run.jar health | grep -E 'openai|claude'
MODELS=openai-gpt-6-luna,openai-gpt-6.1-sol scripts/full-benchmark.sh --label=full-cloud-openai
MODELS=claude-haiku-5.5,claude-opus-5.5   scripts/full-benchmark.sh --label=full-cloud-anthropic
```

A full run costs about 975 calls per model. These models are never selected implicitly: you must name them in
`--models`.

To keep measurements clean, close heavy applications, keep the Mac on power, and don't build or test while a
benchmark runs.

## 6. Podman Compose for the supporting services (optional)

```bash
OLLAMA_HOST=0.0.0.0:11434 ollama serve          # Ollama must listen beyond localhost for the Podman VM
podman compose -f deployments/podman/compose.yaml up --build
```

Containers reach the native Ollama at `http://host.containers.internal:11434`, which is Podman's name for the
host gateway. By default Ollama listens only on 127.0.0.1, which the Podman VM cannot reach. That is why
`OLLAMA_HOST=0.0.0.0` is needed: it opens Ollama to your local network, so only use it on a trusted network. In the
compose setup GLiNER runs inside the Linux VM on CPU, which is slower than native and has different resource
numbers.

## 7. Tests

```bash
cd backend && mvn verify                         # unit + contract + API tests (no models needed)
cd backend && mvn verify -Plive                  # live tests against local Ollama / GLiNER (skipped if absent)
cd inference/gliner && .venv/bin/python -m pytest -q tests
cd frontend && npm ci && npm run build
```

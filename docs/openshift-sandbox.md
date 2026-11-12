# Red Hat OpenShift Developer Sandbox (Profile B)

This was verified on 2026-11-07 against a Developer Sandbox cluster (`api.rm1.0a51.p1.openshiftapps.com`, namespace
`rh-ee-mvandepe-dev`). Your namespace name will differ.

## 1. Inspect before deploying

```bash
oc login --token=<token> --server=<api-url>
oc projects
deployments/openshift/check-sandbox.sh <namespace>
```

What the inspection found in this Sandbox:

| Item | Observed |
|---|---|
| Long-running pods (`compute-deploy`) | requests.cpu **3**, requests.memory 30 Gi, limits.cpu 30, limits.memory 30 Gi |
| Builds (`compute-build`) | requests.cpu 3, memory 14 Gi |
| GPUs | `requests.nvidia.com/gpu: 0` → **no GPU** |
| Storage | 10 PVCs, 80 Gi; storage classes gp3 (default), gp2, efs-sc; ephemeral 15 Gi |
| LimitRange defaults | 10m / 64Mi requested, 1 CPU / 1000Mi limit per container |
| OpenShift AI | KServe `InferenceService`, `ServingRuntime`, `LLMInferenceService`; runtime templates for vLLM (CPU/CUDA/…), OVMS, MLServer |
| Permissions | can create deployments, services, routes, buildconfigs, PVCs, InferenceServices, ServingRuntimes |
| Existing workloads | `decision-gateway`, `quarkus-ai-styles-demo` (unrelated, **not modified**) |

## 2. What runs where

| Component | Sandbox | Why |
|---|---|---|
| GLiNER2.5-base | ✅ custom CPU workload (FastAPI image, PVC for weights) | 194M-parameter encoder, ~0.8 GB weights, works on 1–2 vCPU |
| Quarkus backend + dashboard | ✅ | small JVM / nginx |
| Kev `*-mlx-*` | ❌ not deployable | the MLX runner needs Apple Silicon |
| Gemma 4 12B as a decision model (`gemma4-12b-decision`) | ⚠️ optional (`optional/ollama-cpu.yaml` + `ollama pull gemma4:12b`), verified 2026-11-10 | runs, but ≈ 54 s per message on CPU and needs > 16 Gi memory (24 Gi limit); see benchmark-results.md |
| Qwen3.5-4B, NuExtract3 | ⚠️ not deployed by default | 3–4 GB models on shared CPU are slow and consume most of the 3-CPU request quota; optional manifest provided |

When no Ollama endpoint is reachable, the backend reports the Ollama-based candidates as `DOWN` and the benchmark
skips them with a clear message. To use a separately hosted endpoint, set `BENCH_OLLAMA_BASE_URL` in the
`beyond-llm-config` ConfigMap.

## 3. Deploy

```bash
deployments/openshift/deploy.sh <namespace>
```

The script does the following:
1. Creates three ImageStreams and three Docker-strategy BuildConfigs with **binary** sources.
2. Builds the backend locally with Maven, then uploads `quarkus-app/` + `datasets/` + `Containerfile.prebuilt`. The
   image is UBI 10 OpenJDK 25 runtime, and no Maven runs inside the cluster.
3. Uploads the GLiNER service (UBI 10 Python 3.12 minimal, CPU-only torch wheels) and the frontend (UBI 9 Node 22
   build → UBI 9 nginx 1.24).
4. Applies `deployments/openshift` (kustomize) with the namespace substituted, restarts the three Deployments (they
   use `:latest` with pull policy Always, so a rebuilt image alone does not trigger a rollout), and waits for them.

Equivalent manual steps:

```bash
NS=<namespace>
oc apply -n $NS -f deployments/openshift/buildconfigs.yaml
oc start-build beyond-llm-backend  -n $NS --from-dir=<dir with quarkus-app/, datasets/, Containerfile.prebuilt> --follow
oc start-build beyond-llm-gliner   -n $NS --from-dir=<dir with app.py, requirements.txt, Containerfile> --follow
oc start-build beyond-llm-frontend -n $NS --from-dir=<frontend dir without node_modules> --follow
oc kustomize deployments/openshift | sed "s/REPLACE_NAMESPACE/$NS/g" | oc apply -n $NS -f -
oc rollout restart deploy/beyond-llm-gliner deploy/beyond-llm-backend deploy/beyond-llm-frontend -n $NS
```

Resource requests: GLiNER 1 CPU / 4 Gi (limit 2 / 12 Gi; an earlier 3 Gi limit was OOMKilled on the 2,000–5,000-token inputs), backend 250m / 512 Mi (limit 1 / 1 Gi), frontend
50m / 64 Mi. The total is 1.3 CPU of the 3-CPU request quota. All containers are non-root, run with any UID, drop
all capabilities, use the RuntimeDefault seccomp profile, and have readiness/liveness (and for GLiNER, startup)
probes. The Routes are edge-TLS.

To verify the deployment:

```bash
API=$(oc get route beyond-llm-api -n $NS -o jsonpath='{.spec.host}')
curl -s https://$API/api/models/gliner2.5-base/health
curl -s https://$API/api/inference -H 'Content-Type: application/json' \
  -d '{"modelId":"gliner2.5-base","task":"COMBINED","text":"Order BE-2026-84921 arrived cold and I would like my money back."}'
```

To run a benchmark in-cluster, call `POST /api/benchmarks` on the API route (see docs/benchmark-results.md for the
exact payload used), or use the dashboard's Runner page.

## 4. Custom workload vs. OpenShift AI model serving

* **Custom workload (default, `gliner.yaml`):** a plain Deployment + Service. It is the simplest option and fully
  under your control.
* **OpenShift AI / KServe (`optional/gliner-kserve.yaml`):** registers the *same image* as a custom
  `ServingRuntime` and serves it with an `InferenceService` in RawDeployment mode. OpenShift AI's bundled runtimes
  can't do this themselves: vLLM serves generative LLMs, OVMS serves OpenVINO/ONNX models, and MLServer needs a
  runtime implementation. None of them speaks GLiNER's span-extraction API, so a custom runtime is required. This
  manifest has not been applied or verified in this Sandbox; it is provided as a starting point.
* **vLLM CPU runtime:** could in principle serve Qwen3.5-4B through the OpenAI-compatible API. This project has
  not verified that, and the CPU quota makes it slow. Kev and NuExtract3's template roles are Ollama-specific
  protocols.

## 5. Clean up (only this project's resources)

```bash
oc delete -n $NS all,pvc,configmap,route,bc,is -l app.kubernetes.io/part-of=beyond-llm-benchmark
oc delete -n $NS bc/beyond-llm-backend bc/beyond-llm-gliner bc/beyond-llm-frontend is/beyond-llm-backend is/beyond-llm-gliner is/beyond-llm-frontend --ignore-not-found
```

**Do not compare Sandbox numbers with MacBook numbers as like-for-like:** the hardware, CPU sharing and network path
all differ.

# Benchmark platform — frontend

React + Vite + TypeScript + Tailwind CSS v4 + Recharts dashboard for the
"Beyond the LLM" benchmarking backend (Quarkus). All numbers shown come from the
backend API; missing metrics render as `n/a`. Synthetic dataset — results do not
generalize to production customers.

Pages: **Playground** (`/`), **Runner** (`/runner`), **Results** (`/results/:runId`),
**Inspect** (`/inspect/:runId`).

## Develop

```sh
npm install
npm run dev            # http://localhost:5173, proxies /api -> http://localhost:8080
VITE_API_TARGET=http://other-host:8080 npm run dev   # different backend
```

## Build

```sh
npm run build          # type-check (tsc) + vite build -> dist/
npm run preview        # serve dist/ locally (same /api proxy)
```

The app uses relative `/api` URLs, so in production it must be served from the
same origin as the API (the container below does that).

## Container

Multi-stage build (`ubi9/nodejs-22` → `ubi9/nginx-124`), runs non-root on port
8080 and works with an arbitrary UID (OpenShift). nginx serves `dist/` with SPA
fallback and proxies `/api/` to `BACKEND_URL` (default `http://benchmark-backend:8080`).

```sh
podman build -t benchmark-frontend -f Containerfile .
podman run --rm -p 8081:8080 -e BACKEND_URL=http://host.containers.internal:8080 benchmark-frontend
```

`BACKEND_URL` is resolved when nginx starts, so the backend host name must be
resolvable at that point (e.g. the Kubernetes Service must exist).

## Layout

- `src/api.ts` — typed API client and contract types
- `src/pages/*.tsx` — Playground, Runner, Results, Inspect
- `src/components/*.tsx` — layout, UI primitives, charts, prediction views
- `src/colors.ts` — categorical palette, stable per `modelId`
- `deploy/` — nginx config template and start script used by the `Containerfile`

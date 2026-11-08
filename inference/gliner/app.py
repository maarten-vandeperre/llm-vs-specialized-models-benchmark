"""GLiNER2 inference service for the "Beyond the LLM" benchmark.

Wraps the official `gliner2` library (fastino-ai/GLiNER2) behind a tiny REST API.
* Models are loaded once at startup (never in the request path).
* Inputs longer than the model's max_len are never silently truncated: entity-only requests use the
  library's official long-context chunking (`extract_entities_long`), everything else returns HTTP 413.

Environment:
  GLINER_MODELS   comma separated HF checkpoints (default fastino/gliner2.5-base-v1)
  GLINER_DEVICE   cpu | mps | cuda | auto (default cpu – most reproducible, works in containers)
  GLINER_THREADS  torch intra-op threads (default: torch default)
  GLINER_CHUNK_SIZE / GLINER_CHUNK_OVERLAP  long-context chunking (defaults 384 / 64, library defaults)
"""
from __future__ import annotations

import logging
import os
import platform
import resource
import sys
import threading
import time
from contextlib import asynccontextmanager
from importlib.metadata import PackageNotFoundError, version
from typing import Any, Dict, Optional

from fastapi import FastAPI
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

log = logging.getLogger("gliner-service")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")

DEFAULT_MODELS = "fastino/gliner2.5-base-v1"


def _pkg_version(name: str) -> Optional[str]:
    try:
        return version(name)
    except PackageNotFoundError:
        return None


def resolve_device(requested: str) -> str:
    requested = (requested or "cpu").lower()
    if requested != "auto":
        return requested
    try:
        import torch

        if torch.cuda.is_available():
            return "cuda"
        if getattr(torch.backends, "mps", None) and torch.backends.mps.is_available():
            return "mps"
    except Exception:  # pragma: no cover - torch import problems are reported via /health
        pass
    return "cpu"


def _hf_storage_bytes(checkpoint: str) -> Optional[int]:
    """Size of the checkpoint in the local Hugging Face cache (None if not determinable)."""
    try:
        from huggingface_hub import scan_cache_dir

        for repo in scan_cache_dir().repos:
            if repo.repo_id == checkpoint:
                return int(repo.size_on_disk)
    except Exception:
        return None
    return None


class LoadedModel:
    def __init__(self, checkpoint: str, model: Any, device: str, load_seconds: float):
        self.checkpoint = checkpoint
        self.model = model
        self.device = device
        self.load_seconds = load_seconds
        self.lock = threading.Lock()  # one inference at a time per model: measured, documented behaviour
        cfg = getattr(model, "config", None)
        self.architecture = getattr(cfg, "architecture", None)
        self.max_tokens = int(os.getenv("GLINER_MAX_TOKENS", getattr(cfg, "max_len", 0) or 512))
        self.storage_bytes = _hf_storage_bytes(checkpoint)
        tok = getattr(model, "tokenizer", None) or getattr(getattr(model, "processor", None), "tokenizer", None)
        self.tokenizer = tok

    def count_tokens(self, text: str) -> Optional[int]:
        if self.tokenizer is None:
            return None
        try:
            return len(self.tokenizer(text, add_special_tokens=True)["input_ids"])
        except Exception:
            return None


class ModelStore:
    """Holds loaded models; injectable for tests."""

    def __init__(self) -> None:
        self.models: Dict[str, LoadedModel] = {}
        self.errors: Dict[str, str] = {}

    def load_all(self, checkpoints: list[str], device: str) -> None:
        from gliner2 import AutoExtractor  # imported lazily so tests can run without torch

        for ckpt in checkpoints:
            t0 = time.perf_counter()
            try:
                model = AutoExtractor.from_pretrained(ckpt, map_location=device)
                self.models[ckpt] = LoadedModel(ckpt, model, device, time.perf_counter() - t0)
                log.info("Loaded %s on %s in %.1fs", ckpt, device, self.models[ckpt].load_seconds)
            except Exception as e:  # keep serving the other models, report the failure
                self.errors[ckpt] = f"{type(e).__name__}: {e}"
                log.exception("Failed to load %s", ckpt)


store = ModelStore()


@asynccontextmanager
async def lifespan(_: FastAPI):
    threads = os.getenv("GLINER_THREADS")
    if threads:
        import torch

        torch.set_num_threads(int(threads))
    if not store.models and os.getenv("GLINER_SKIP_LOAD") != "1":
        ckpts = [c.strip() for c in os.getenv("GLINER_MODELS", DEFAULT_MODELS).split(",") if c.strip()]
        store.load_all(ckpts, resolve_device(os.getenv("GLINER_DEVICE", "cpu")))
    yield


app = FastAPI(title="GLiNER2 inference service", version="1.0.0", lifespan=lifespan)


class ExtractRequest(BaseModel):
    model: str
    text: str
    entities: Optional[Dict[str, str]] = Field(default=None, description="label -> description")
    classifications: Optional[Dict[str, Dict[str, str]]] = Field(default=None, description="task -> {label: description}")
    threshold: float = 0.5


def _rss_bytes() -> Optional[int]:
    try:
        import psutil

        return psutil.Process().memory_info().rss
    except Exception:
        return None


def _peak_rss_bytes() -> int:
    peak = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
    return peak if sys.platform == "darwin" else peak * 1024  # macOS reports bytes, Linux KiB


@app.get("/health")
def health() -> Dict[str, Any]:
    return {
        "status": "UP" if store.models else "DOWN",
        "models": {
            k: {
                "loaded": True,
                "device": m.device,
                "architecture": m.architecture,
                "max_tokens": m.max_tokens,
                "load_seconds": round(m.load_seconds, 3),
                "storage_bytes": m.storage_bytes,
            }
            for k, m in store.models.items()
        },
        "errors": store.errors,
        "versions": {
            "python": platform.python_version(),
            "gliner2": _pkg_version("gliner2"),
            "torch": _pkg_version("torch"),
            "transformers": _pkg_version("transformers"),
        },
    }


@app.get("/stats")
def stats() -> Dict[str, Any]:
    t = os.times()
    return {"pid": os.getpid(), "rss_bytes": _rss_bytes(), "peak_rss_bytes": _peak_rss_bytes(),
            "cpu_user_seconds": t.user, "cpu_system_seconds": t.system}


def run_extraction(m: LoadedModel, req: ExtractRequest) -> Dict[str, Any]:
    tokens = m.count_tokens(req.text)
    too_long = tokens is not None and tokens > m.max_tokens
    model = m.model
    kwargs = dict(threshold=req.threshold, include_confidence=True, include_spans=True)
    chunked = False
    with m.lock:
        t0 = time.perf_counter()
        if too_long:
            if req.classifications:
                return {"_status": 413, "error": "input_too_long",
                        "detail": f"{tokens} tokens > max_len {m.max_tokens}; classification over chunked input is not "
                                  f"supported without an aggregation rule, so the request is rejected instead of truncated",
                        "input_tokens": tokens, "max_tokens": m.max_tokens}
            result = model.extract_entities_long(req.text, req.entities or {},
                                                 chunk_size=int(os.getenv("GLINER_CHUNK_SIZE", "384")),
                                                 chunk_overlap=int(os.getenv("GLINER_CHUNK_OVERLAP", "64")),
                                                 include_confidence=True, include_spans=True)
            chunked = True
        elif req.entities and req.classifications:
            schema = model.create_schema().entities(req.entities)
            for name, labels in req.classifications.items():
                schema = schema.classification(name, labels)
            result = model.extract(req.text, schema, **kwargs)
        elif req.classifications:
            tasks = {name: {"labels": labels} for name, labels in req.classifications.items()}
            result = model.classify_text(req.text, tasks, threshold=req.threshold, include_confidence=True)
        elif req.entities:
            result = model.extract_entities(req.text, req.entities, **kwargs)
        else:
            return {"_status": 400, "error": "nothing to do: provide entities and/or classifications"}
        elapsed = time.perf_counter() - t0
    return {"model": m.checkpoint, "result": result, "inference_ms": elapsed * 1000.0, "input_tokens": tokens,
            "max_tokens": m.max_tokens, "chunked": chunked, "device": m.device}


@app.post("/v1/extract")
async def extract(req: ExtractRequest):
    m = store.models.get(req.model)
    if m is None:
        return JSONResponse(status_code=404, content={"error": f"model {req.model} not loaded",
                                                      "loaded": list(store.models), "load_errors": store.errors})
    try:
        out = await run_in_threadpool(run_extraction, m, req)
    except Exception as e:  # surfaced to the Java client as INFRA_ERROR with message
        log.exception("inference failed")
        return JSONResponse(status_code=500, content={"error": f"{type(e).__name__}: {e}"})
    status = out.pop("_status", 200)
    return JSONResponse(status_code=status, content=_jsonable(out))


def _jsonable(o: Any) -> Any:
    if isinstance(o, dict):
        return {str(k): _jsonable(v) for k, v in o.items()}
    if isinstance(o, (list, tuple)):
        return [_jsonable(v) for v in o]
    if isinstance(o, float):
        return float(o)
    if hasattr(o, "item"):
        return o.item()
    return o


if __name__ == "__main__":  # pragma: no cover
    import uvicorn

    uvicorn.run(app, host=os.getenv("HOST", "0.0.0.0"), port=int(os.getenv("PORT", "8090")))

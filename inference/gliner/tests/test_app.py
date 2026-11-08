"""Unit tests for the GLiNER service using a fake model (no downloads, no torch needed)."""
import os

os.environ["GLINER_SKIP_LOAD"] = "1"

from fastapi.testclient import TestClient  # noqa: E402

import app as service  # noqa: E402


class FakeTokenizer:
    def __call__(self, text, add_special_tokens=True):
        return {"input_ids": text.split()}


class FakeSchema:
    def __init__(self):
        self.parts = []

    def entities(self, e):
        self.parts.append(("entities", e))
        return self

    def classification(self, name, labels):
        self.parts.append(("classification", name, labels))
        return self


class FakeModel:
    class config:  # noqa: N801
        architecture = "boundary"
        max_len = 10

    tokenizer = FakeTokenizer()

    def __init__(self):
        self.calls = []

    def extract_entities(self, text, entities, **kw):
        self.calls.append("extract_entities")
        return {"entities": {"order_id": [{"text": "BE-1", "start": 6, "end": 10, "confidence": 0.9}]}}

    def extract_entities_long(self, text, entities, **kw):
        self.calls.append("extract_entities_long")
        return {"entities": {"order_id": []}}

    def classify_text(self, text, tasks, **kw):
        self.calls.append("classify_text")
        return {k: {"label": list(v["labels"])[0], "confidence": 0.7} for k, v in tasks.items()}

    def create_schema(self):
        return FakeSchema()

    def extract(self, text, schema, **kw):
        self.calls.append("extract")
        return {"entities": {}, "intent": {"label": "refund", "confidence": 0.8}}


def client_with(model):
    service.store.models.clear()
    service.store.models["fake/model"] = service.LoadedModel("fake/model", model, "cpu", 1.5)
    return TestClient(service.app)


def test_health_reports_models_and_versions():
    c = client_with(FakeModel())
    body = c.get("/health").json()
    assert body["status"] == "UP"
    assert body["models"]["fake/model"]["max_tokens"] == 10
    assert body["models"]["fake/model"]["load_seconds"] == 1.5
    assert "gliner2" in body["versions"]


def test_entities_only_uses_extract_entities():
    m = FakeModel()
    c = client_with(m)
    r = c.post("/v1/extract", json={"model": "fake/model", "text": "Order BE-1 please", "entities": {"order_id": "id"}})
    assert r.status_code == 200
    assert r.json()["result"]["entities"]["order_id"][0]["start"] == 6
    assert m.calls == ["extract_entities"]
    assert r.json()["chunked"] is False


def test_classification_only_uses_classify_text():
    m = FakeModel()
    c = client_with(m)
    r = c.post("/v1/extract", json={"model": "fake/model", "text": "money back", "classifications": {"intent": {"refund": "d", "none": "d"}}})
    assert r.status_code == 200
    assert r.json()["result"]["intent"]["label"] == "refund"
    assert m.calls == ["classify_text"]


def test_combined_uses_single_extract_call():
    m = FakeModel()
    c = client_with(m)
    r = c.post("/v1/extract", json={"model": "fake/model", "text": "x", "entities": {"a": "b"},
                                    "classifications": {"intent": {"refund": "d"}}})
    assert r.status_code == 200
    assert m.calls == ["extract"]


def test_long_entity_input_is_chunked_not_truncated():
    m = FakeModel()
    c = client_with(m)
    text = " ".join(["word"] * 50)
    r = c.post("/v1/extract", json={"model": "fake/model", "text": text, "entities": {"a": "b"}})
    assert r.status_code == 200
    assert r.json()["chunked"] is True
    assert m.calls == ["extract_entities_long"]


def test_long_classification_input_is_rejected_with_413():
    m = FakeModel()
    c = client_with(m)
    text = " ".join(["word"] * 50)
    r = c.post("/v1/extract", json={"model": "fake/model", "text": text, "classifications": {"intent": {"x": "y"}}})
    assert r.status_code == 413
    assert r.json()["error"] == "input_too_long"
    assert r.json()["input_tokens"] == 50
    assert m.calls == []


def test_unknown_model_returns_404():
    c = client_with(FakeModel())
    r = c.post("/v1/extract", json={"model": "nope", "text": "x", "entities": {"a": "b"}})
    assert r.status_code == 404


def test_stats_endpoint():
    c = client_with(FakeModel())
    s = c.get("/stats").json()
    assert s["pid"] > 0
    assert s["peak_rss_bytes"] > 0

# Superseded run

This first full MacBook run is kept for transparency. **Do not quote its throughput numbers for Kev-based candidates.**

* The old phase design warmed up on the first records and replayed the latency sample in the throughput phase.
  Kev's `/v1/systemone` answers a fully cached prompt in ~4–20 ms, so `kev-0.8b`, `kev-0.8b-candsel` and
  `hybrid-gliner-kev` throughput (300+ req/s) measured Ollama's prompt cache, not inference.
* `report.md` was regenerated with the fixed report code (cache hits excluded from headline latency and counted),
  but the throughput phase itself cannot be repaired after the fact.
* Quality numbers are valid: they come from the quality pass, where each record is sent once.

Canonical MacBook results: see `docs/benchmark-results.md`.

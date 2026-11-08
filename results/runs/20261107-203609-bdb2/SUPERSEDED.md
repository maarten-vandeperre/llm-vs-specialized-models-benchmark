# Superseded run (OpenShift Sandbox)

The GLiNER pod (memory limit 3 Gi) was **OOMKilled** at 20:45:53Z while processing the 2,000–5,000-token inputs
near the end of the quality pass. All 120 latency-phase and 120 throughput requests then failed with
`Connection refused`, and 14 quality requests failed. The latency and throughput sections of this report are
therefore invalid. The limit was raised to 12 Gi and the run was repeated; see `docs/benchmark-results.md`.

Files were downloaded from the in-cluster API (`report.json`, `report.md`, `predictions.csv`, `status.json`).

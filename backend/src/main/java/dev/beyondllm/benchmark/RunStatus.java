package dev.beyondllm.benchmark;

import java.time.Instant;
import java.util.List;

/** Snapshot of a run's lifecycle, returned by GET /api/benchmarks/{runId}. */
public record RunStatus(String runId, String label, State status, Progress progress, List<String> errors, BenchmarkConfig config,
                        Instant createdAt, Instant startedAt, Instant finishedAt) {

    public enum State { QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED }

    public record Progress(long completed, long total, String phase, String currentModel) {
    }
}

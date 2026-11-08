package dev.beyondllm.domain;

import java.util.Map;

/**
 * Result of one call. The raw provider payload is always preserved for troubleshooting.
 */
public record BenchmarkResponse(String modelId, String requestId, TaskType task, Outcome outcome, Prediction prediction,
                                String error, Timing timing, String rawResponse, Map<String, Object> metadata) {

    public BenchmarkResponse {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static BenchmarkResponse success(String modelId, BenchmarkRequest req, Prediction prediction, Timing timing,
                                            String raw, Map<String, Object> metadata) {
        return new BenchmarkResponse(modelId, req.requestId(), req.task(), Outcome.SUCCESS, prediction, null, timing, raw, metadata);
    }

    public static BenchmarkResponse failure(String modelId, BenchmarkRequest req, Outcome outcome, String error, Timing timing, String raw) {
        if (outcome == Outcome.SUCCESS) {
            throw new IllegalArgumentException("failure() requires a non-success outcome");
        }
        return new BenchmarkResponse(modelId, req.requestId(), req.task(), outcome, null, error,
                timing == null ? Timing.client(0) : timing, raw, Map.of());
    }

    public static BenchmarkResponse unsupported(String modelId, BenchmarkRequest req, String reason) {
        return failure(modelId, req, Outcome.UNSUPPORTED, reason, Timing.client(0), null);
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isSuccess() {
        return outcome == Outcome.SUCCESS;
    }

    public BenchmarkResponse withTiming(Timing newTiming) {
        return new BenchmarkResponse(modelId, requestId, task, outcome, prediction, error, newTiming, rawResponse, metadata);
    }

    public BenchmarkResponse withModelId(String id) {
        return new BenchmarkResponse(id, requestId, task, outcome, prediction, error, timing, rawResponse, metadata);
    }
}

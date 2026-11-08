package dev.beyondllm.application;

import dev.beyondllm.domain.BenchmarkRequest;
import dev.beyondllm.domain.BenchmarkResponse;
import dev.beyondllm.domain.HealthStatus;
import dev.beyondllm.domain.ModelCapabilities;

/**
 * Model-independent port implemented by every adapter and hybrid pipeline.
 * Implementations must never throw from {@link #execute}: every failure is mapped to an {@code Outcome}.
 */
public interface BenchmarkModel {

    String id();

    ModelInfo info();

    ModelCapabilities capabilities();

    HealthStatus health();

    BenchmarkResponse execute(BenchmarkRequest request);

    /** Unload / reset the model so the next request measures a cold start. Returns false if not supported. */
    default boolean unload() {
        return false;
    }

    /**
     * True for paid commercial APIs: never selected implicitly (e.g. "all healthy candidates"), only when named
     * explicitly, so a run never spends money by accident.
     */
    default boolean requiresExplicitSelection() {
        return false;
    }

    /** Runtime model names this candidate depends on (e.g. Ollama tags), used for contention control. */
    default java.util.List<String> runtimeModels() {
        return java.util.List.of();
    }
}

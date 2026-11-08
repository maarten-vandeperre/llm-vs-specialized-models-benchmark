package dev.beyondllm.domain;

import java.util.Map;

/**
 * Timing and token accounting for one call. Nullable fields mean "not reported by the provider" – they are never estimated.
 *
 * @param clientNanos       wall-clock latency measured in the Java client around the full call (incl. HTTP + JSON)
 * @param serverNanos       provider-reported total duration (Ollama total_duration, GLiNER service inference time)
 * @param loadNanos         provider-reported model load duration (Ollama load_duration)
 * @param inputTokens       provider-reported input tokens (Ollama prompt_eval_count, System One usage.input_tokens)
 * @param outputTokens      provider-reported generated tokens (Ollama eval_count, System One usage.output_tokens)
 * @param generationNanos   provider-reported generation duration (Ollama eval_duration) for tokens/s
 * @param stageNanos        per-stage client timings for pipelines and candidate pre-processing
 */
public record Timing(long clientNanos, Long serverNanos, Long loadNanos, Integer inputTokens, Integer outputTokens,
                     Long generationNanos, Map<String, Long> stageNanos) {

    public Timing {
        stageNanos = stageNanos == null ? Map.of() : Map.copyOf(stageNanos);
    }

    public static Timing client(long clientNanos) {
        return new Timing(clientNanos, null, null, null, null, null, Map.of());
    }

    public Timing withClientNanos(long nanos) {
        return new Timing(nanos, serverNanos, loadNanos, inputTokens, outputTokens, generationNanos, stageNanos);
    }

    public Timing withStages(Map<String, Long> stages) {
        return new Timing(clientNanos, serverNanos, loadNanos, inputTokens, outputTokens, generationNanos, stages);
    }

    public double clientMillis() {
        return clientNanos / 1_000_000.0;
    }
}

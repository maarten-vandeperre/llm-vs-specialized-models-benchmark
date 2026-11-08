package dev.beyondllm.domain;

import java.util.Map;

/** Health of a model's runtime, reported without throwing so unavailable runtimes are surfaced cleanly. */
public record HealthStatus(String modelId, State state, String detail, Map<String, Object> info) {

    public enum State {
        /** Runtime reachable and model present. */
        UP,
        /** Runtime reachable but the model tag/checkpoint is not installed. */
        MODEL_MISSING,
        /** Runtime reachable but too old / lacks the required capability (e.g. Ollama &lt; 0.40.4 for Kev). */
        RUNTIME_UNSUPPORTED,
        /** Runtime not reachable. */
        DOWN,
        /** Candidate disabled by configuration. */
        DISABLED
    }

    public static HealthStatus up(String id, String detail, Map<String, Object> info) {
        return new HealthStatus(id, State.UP, detail, info);
    }

    public static HealthStatus of(String id, State state, String detail) {
        return new HealthStatus(id, state, detail, Map.of());
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isUp() {
        return state == State.UP;
    }
}

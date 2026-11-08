package dev.beyondllm.domain;

import java.util.Objects;

/**
 * A typed, model-independent request. All candidates receive exactly the same text and label schema
 * ({@link LabelSchema}); only the protocol used to ask differs per adapter.
 */
public record BenchmarkRequest(String requestId, TaskType task, String text, String language) {

    public BenchmarkRequest {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(text, "text");
        if (requestId == null || requestId.isBlank()) {
            requestId = "adhoc-" + Integer.toHexString(text.hashCode());
        }
    }

    public BenchmarkRequest withTask(TaskType newTask) {
        return new BenchmarkRequest(requestId, newTask, text, language);
    }
}

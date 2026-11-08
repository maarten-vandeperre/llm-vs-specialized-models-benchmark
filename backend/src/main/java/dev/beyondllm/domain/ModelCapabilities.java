package dev.beyondllm.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Declared capabilities of a benchmark candidate.
 *
 * @param features        native features
 * @param tasks           tasks the candidate accepts; other tasks yield {@link Outcome#UNSUPPORTED}
 * @param maxInputTokens  documented maximum input length in the model's own tokens, or null if unknown
 */
public record ModelCapabilities(Set<Capability> features, Set<TaskType> tasks, Integer maxInputTokens) {

    public ModelCapabilities {
        features = features.isEmpty() ? EnumSet.noneOf(Capability.class) : EnumSet.copyOf(features);
        tasks = tasks.isEmpty() ? EnumSet.noneOf(TaskType.class) : EnumSet.copyOf(tasks);
    }

    public boolean supports(TaskType task) {
        return tasks.contains(task);
    }

    public boolean has(Capability capability) {
        return features.contains(capability);
    }
}

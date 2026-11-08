package dev.beyondllm.domain;

import java.util.Map;

/**
 * A predicted label.
 *
 * @param label              the chosen label (normalized to the {@link LabelSchema} vocabulary when possible, else as returned)
 * @param probability        provider-native score of the chosen label, or {@code null} if the provider returns none.
 *                           Kev: probabilities[choice]. GLiNER: label confidence. Never fabricated.
 * @param distribution       full distribution over labels when natively available (Kev), else {@code null}
 * @param providerConfidence provider-specific "confidence" field with its own semantics (Kev: 1 - H(p)/ln N,
 *                           a concentration measure, NOT probability of correctness), else {@code null}
 */
public record LabelPrediction(String label, Double probability, Map<String, Double> distribution, Double providerConfidence) {

    public static LabelPrediction of(String label) {
        return new LabelPrediction(label, null, null, null);
    }
}

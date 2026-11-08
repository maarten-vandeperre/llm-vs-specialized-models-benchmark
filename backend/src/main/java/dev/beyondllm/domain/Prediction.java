package dev.beyondllm.domain;

import java.util.List;
import java.util.Map;

/**
 * Normalized prediction.
 *
 * @param labels     classification field name → label (empty when not requested / not supported)
 * @param extraction extraction result or null
 * @param decision   business decision, only set by hybrid pipelines inline (evaluation re-derives it for every model)
 * @param route      pipeline stages actually executed, e.g. ["gliner", "kev", "qwen-fallback"]
 */
public record Prediction(Map<String, LabelPrediction> labels, Extraction extraction, String decision, List<String> route) {

    public Prediction {
        labels = labels == null ? Map.of() : Map.copyOf(labels);
        route = route == null ? List.of() : List.copyOf(route);
    }

    public static Prediction of(Map<String, LabelPrediction> labels, Extraction extraction) {
        return new Prediction(labels, extraction, null, List.of());
    }

    public String label(String field) {
        LabelPrediction p = labels.get(field);
        return p == null ? null : p.label();
    }
}

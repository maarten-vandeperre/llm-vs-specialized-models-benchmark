package dev.beyondllm.evaluation;

import java.util.List;
import java.util.Map;

/** Per-record scoring result, persisted with every prediction for inspection. */
public record RecordEvaluation(Map<String, FieldEval> classification, ExtractionEval extraction, DecisionEval decision, boolean correct) {

    public record FieldEval(String gold, String predicted, boolean correct, Double probability) {
    }

    public record ExtractionEval(boolean exactMatch, int tp, int fp, int fn, List<String> hallucinated, List<String> missing,
                                 Map<String, Boolean> fieldCorrect, Boolean offsetCorrect, int predictedTuples) {
    }

    public record DecisionEval(String gold, String predicted, boolean correct) {
    }
}

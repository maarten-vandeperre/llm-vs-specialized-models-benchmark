package dev.beyondllm.domain;

import java.util.List;

/**
 * Normalized extraction structure. Values are kept as the raw strings the model produced
 * ("two", "2", "€12.50"); value normalization happens once, identically for all models, in the evaluator.
 */
public record Extraction(String orderId, Span orderIdSpan, List<LineItem> items, String total, Span totalSpan, String currency) {

    public Extraction {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static Extraction empty() {
        return new Extraction(null, null, List.of(), null, null, null);
    }

    /** @param quantity raw quantity text or number as string; null when the model gave none */
    public record LineItem(String product, String quantity, Span productSpan) {
    }
}

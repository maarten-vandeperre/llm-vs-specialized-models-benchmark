package dev.beyondllm.domain;

/**
 * The benchmark task a request asks a model to perform.
 * <ul>
 *     <li>{@code CLASSIFICATION} – assign fixed labels (intent, sentiment, category, complaint).</li>
 *     <li>{@code EXTRACTION} – extract order id, line items, total and currency.</li>
 *     <li>{@code COMBINED} – both in one logical request (one call or a pipeline).</li>
 * </ul>
 */
public enum TaskType {
    CLASSIFICATION,
    EXTRACTION,
    COMBINED;

    public boolean needsClassification() {
        return this == CLASSIFICATION || this == COMBINED;
    }

    public boolean needsExtraction() {
        return this == EXTRACTION || this == COMBINED;
    }
}

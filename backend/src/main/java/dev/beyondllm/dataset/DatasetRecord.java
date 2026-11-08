package dev.beyondllm.dataset;

import dev.beyondllm.domain.TaskType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * One synthetic benchmark record. Ground truth comes exclusively from the generator's explicit rules
 * (or from hand-authored fixtures), never from any evaluated model.
 */
public record DatasetRecord(String id, String group, String split, String language, TaskType task, String text,
                            int approxTokens, List<String> tags, Map<String, Object> meta, Gold gold) {

    public record SpanRef(int start, int end) {
    }

    public record GoldItem(String product, int quantity) {
    }

    /** All fields are populated for every record; {@link #task} decides which ones are scored. */
    public record Gold(String intent, String sentiment, String category, String complaint,
                       String orderId, SpanRef orderIdSpan, List<GoldItem> items, BigDecimal total, String currency,
                       SpanRef totalSpan, String decision) {
        public String label(String field) {
            return switch (field) {
                case "intent" -> intent;
                case "sentiment" -> sentiment;
                case "category" -> category;
                case "complaint" -> complaint;
                default -> throw new IllegalArgumentException(field);
            };
        }
    }
}

package dev.beyondllm.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import dev.beyondllm.domain.Extraction;
import dev.beyondllm.domain.LabelPrediction;
import dev.beyondllm.domain.LabelSchema;
import dev.beyondllm.domain.Prediction;
import dev.beyondllm.domain.TaskType;
import dev.beyondllm.infrastructure.http.JsonHttp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps the JSON object produced by generative extractors (NuExtract3, Qwen) to a {@link Prediction}.
 * Shared output keys: intent, sentiment, category, complaint, order_id, items[{product, quantity}], total_amount, currency.
 * Generative models return no per-label probabilities, so {@code probability} stays null (never invented).
 */
public final class GenerativeOutputMapper {

    private GenerativeOutputMapper() {
    }

    public static final class MalformedOutputException extends Exception {
        public MalformedOutputException(String message) {
            super(message);
        }
    }

    public static Prediction map(String content, TaskType task) throws MalformedOutputException {
        if (content == null || content.isBlank()) {
            throw new MalformedOutputException("Empty model output");
        }
        JsonNode root;
        try {
            root = JsonHttp.MAPPER.readTree(JsonHttp.stripFences(content));
        } catch (Exception e) {
            throw new MalformedOutputException("Output is not valid JSON: " + e.getMessage());
        }
        if (root == null || !root.isObject()) {
            throw new MalformedOutputException("Output is not a JSON object");
        }
        Map<String, LabelPrediction> labels = new LinkedHashMap<>();
        if (task.needsClassification()) {
            for (String field : LabelSchema.FIELDS) {
                JsonNode v = root.get(field);
                if (v == null) {
                    throw new MalformedOutputException("Missing classification field '" + field + "'");
                }
                String label = v.isNull() ? LabelSchema.NONE : LabelSchema.normalize(field, v.asText());
                labels.put(field, LabelPrediction.of(label));
            }
        }
        Extraction extraction = null;
        if (task.needsExtraction()) {
            if (!root.has("order_id") && !root.has("items") && !root.has("total_amount")) {
                throw new MalformedOutputException("Missing all extraction fields");
            }
            List<Extraction.LineItem> items = new ArrayList<>();
            JsonNode arr = root.path("items");
            if (arr.isArray()) {
                for (JsonNode it : arr) {
                    String product = text(it.get("product"));
                    if (product == null) {
                        continue;
                    }
                    items.add(new Extraction.LineItem(product, text(it.get("quantity")), null));
                }
            } else if (!arr.isMissingNode() && !arr.isNull()) {
                throw new MalformedOutputException("'items' is not an array");
            }
            extraction = new Extraction(text(root.get("order_id")), null, items, text(root.get("total_amount")), null,
                    text(root.get("currency")));
        }
        return Prediction.of(labels, extraction);
    }

    private static String text(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) {
            return null;
        }
        String s = n.isTextual() ? n.asText() : n.toString();
        return s.isBlank() ? null : s.trim();
    }
}

package dev.beyondllm.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fixed, language-independent label vocabulary (version {@value #VERSION}). Labels are English identifiers for all
 * languages so that Dutch/French inputs are never scored against translated labels.
 * Every adapter derives its prompt / schema / question set from this class.
 */
public final class LabelSchema {

    public static final String VERSION = "labels-v1";
    public static final String NONE = "none";

    public static final String INTENT = "intent";
    public static final String SENTIMENT = "sentiment";
    public static final String CATEGORY = "category";
    public static final String COMPLAINT = "complaint";

    public static final List<String> FIELDS = List.of(INTENT, SENTIMENT, CATEGORY, COMPLAINT);

    private static final Map<String, Map<String, String>> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        Map<String, String> intent = new LinkedHashMap<>();
        intent.put("refund", "The customer asks for their money back.");
        intent.put("replacement", "The customer asks for a new or replacement product, not money.");
        intent.put("order_status", "The customer asks where an order is or when it will arrive.");
        intent.put("cancel_order", "The customer asks to cancel an order.");
        intent.put("product_question", "The customer asks a question about products, ingredients or the menu.");
        intent.put("feedback", "The customer only shares an opinion or experience and asks for no action.");
        intent.put(NONE, "No clear intent, or the message is too ambiguous to decide.");
        DESCRIPTIONS.put(INTENT, intent);

        Map<String, String> sentiment = new LinkedHashMap<>();
        sentiment.put("positive", "The customer is satisfied or happy.");
        sentiment.put("negative", "The customer is dissatisfied, annoyed or complaining.");
        sentiment.put("neutral", "Neither clearly positive nor clearly negative, including mixed or uncertain feelings.");
        DESCRIPTIONS.put(SENTIMENT, sentiment);

        Map<String, String> category = new LinkedHashMap<>();
        category.put("product_quality", "About the taste or temperature of a product.");
        category.put("delivery", "About a wrong, missing or late delivered item.");
        category.put("billing", "About charges, prices or payments.");
        category.put("service", "About staff behaviour.");
        category.put("general", "Anything else, including praise, questions, status requests and unclear messages.");
        DESCRIPTIONS.put(CATEGORY, category);

        Map<String, String> complaint = new LinkedHashMap<>();
        complaint.put("temperature", "A drink or food item was cold, lukewarm or not hot enough.");
        complaint.put("taste", "A product tasted bad, burnt, sour or wrong.");
        complaint.put("wrong_item", "The customer received a different product than ordered.");
        complaint.put("missing_item", "An ordered product was missing.");
        complaint.put("late_delivery", "The order arrived late or has not arrived yet although it is overdue.");
        complaint.put("overcharged", "The customer was charged too much or charged twice.");
        complaint.put("rude_staff", "Staff were rude or unfriendly.");
        complaint.put(NONE, "There is no complaint.");
        DESCRIPTIONS.put(COMPLAINT, complaint);
    }

    private LabelSchema() {
    }

    public static List<String> labels(String field) {
        return List.copyOf(descriptions(field).keySet());
    }

    public static Map<String, String> descriptions(String field) {
        Map<String, String> d = DESCRIPTIONS.get(field);
        if (d == null) {
            throw new IllegalArgumentException("Unknown classification field: " + field);
        }
        return d;
    }

    /** Deterministic rule linking complaint to category (also used by the dataset generator for gold labels). */
    public static String categoryFor(String complaint) {
        return switch (complaint) {
            case "temperature", "taste" -> "product_quality";
            case "wrong_item", "missing_item", "late_delivery" -> "delivery";
            case "overcharged" -> "billing";
            case "rude_staff" -> "service";
            default -> "general";
        };
    }

    /** Maps a free-form label from a generative model onto the vocabulary; returns the input lower-cased if unknown. */
    public static String normalize(String field, String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim().toLowerCase().replace(' ', '_').replace('-', '_');
        if (v.isEmpty() || v.equals("null") || v.equals("unknown") || v.equals("other") || v.equals("n/a")) {
            return descriptions(field).containsKey(NONE) ? NONE : v;
        }
        return v;
    }

    public static boolean isKnown(String field, String label) {
        return label != null && descriptions(field).containsKey(label);
    }
}

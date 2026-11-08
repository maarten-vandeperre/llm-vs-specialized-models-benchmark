package dev.beyondllm.domain;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Deterministic business rule turning labels + extracted fields into an action. Used three ways:
 * the generator derives the gold decision with it, hybrid pipelines apply it inline, and the evaluator applies
 * it to every model's prediction so that decision accuracy is comparable across all candidates.
 */
public final class DecisionPolicy {

    public static final String AUTO_REFUND = "auto_refund";
    public static final String SEND_REPLACEMENT = "send_replacement";
    public static final String SEND_STATUS = "send_status";
    public static final String HUMAN_REVIEW = "human_review";
    public static final String NO_ACTION = "no_action";

    public static final BigDecimal AUTO_REFUND_LIMIT = new BigDecimal("25.00");
    private static final Set<String> AUTO_REFUNDABLE = Set.of("temperature", "taste", "wrong_item", "missing_item");

    private DecisionPolicy() {
    }

    /**
     * @param intent    normalized intent (may be null)
     * @param complaint normalized complaint (may be null)
     * @param hasOrderId whether an order id is known
     * @param total     normalized total amount or null
     */
    public static String decide(String intent, String complaint, boolean hasOrderId, BigDecimal total) {
        if (intent == null) {
            return HUMAN_REVIEW;
        }
        return switch (intent) {
            case "refund" -> (hasOrderId && complaint != null && AUTO_REFUNDABLE.contains(complaint)
                    && total != null && total.compareTo(AUTO_REFUND_LIMIT) <= 0) ? AUTO_REFUND : HUMAN_REVIEW;
            case "replacement" -> hasOrderId && complaint != null && AUTO_REFUNDABLE.contains(complaint)
                    ? SEND_REPLACEMENT : HUMAN_REVIEW;
            case "order_status" -> hasOrderId ? SEND_STATUS : HUMAN_REVIEW;
            case "feedback", "product_question" -> NO_ACTION;
            default -> HUMAN_REVIEW; // cancel_order, none, unknown → a human handles it
        };
    }
}

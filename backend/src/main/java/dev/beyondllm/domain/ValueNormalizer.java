package dev.beyondllm.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Value normalization applied identically to every model's output before scoring (and to gold values).
 * Rules are intentionally simple and documented in docs/methodology.md.
 */
public final class ValueNormalizer {

    private static final Map<String, Integer> NUMBER_WORDS = Map.ofEntries(
            Map.entry("a", 1), Map.entry("an", 1), Map.entry("one", 1), Map.entry("single", 1),
            Map.entry("two", 2), Map.entry("three", 3), Map.entry("four", 4), Map.entry("five", 5), Map.entry("six", 6),
            Map.entry("een", 1), Map.entry("één", 1), Map.entry("twee", 2), Map.entry("drie", 3), Map.entry("vier", 4),
            Map.entry("vijf", 5), Map.entry("zes", 6),
            Map.entry("un", 1), Map.entry("une", 1), Map.entry("deux", 2), Map.entry("trois", 3), Map.entry("quatre", 4),
            Map.entry("cinq", 5));

    private static final Pattern NUMBER = Pattern.compile("(\\d{1,6})(?:[.,](\\d{1,2}))?");

    private ValueNormalizer() {
    }

    public record Money(BigDecimal amount, String currency) {
    }

    /** Order ids: trimmed, leading '#' and trailing punctuation removed, upper-cased. */
    public static String orderId(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().replaceAll("^[#:\\s]+", "").replaceAll("[.,;:!?)\\]\"']+$", "").trim();
        if (s.isEmpty() || s.equalsIgnoreCase("null") || s.equalsIgnoreCase("none")) {
            return null;
        }
        return s.toUpperCase(Locale.ROOT);
    }

    /** Quantities: digits or number words (en/nl/fr). A missing quantity for a present product counts as 1. */
    public static Integer quantity(String raw) {
        if (raw == null || raw.isBlank()) {
            return 1;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[x×]", "").trim();
        Integer w = NUMBER_WORDS.get(s);
        if (w != null) {
            return w;
        }
        Matcher m = Pattern.compile("\\d+").matcher(s);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static String currency(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.contains("€") || s.contains("eur")) {
            return "EUR";
        }
        if (s.contains("$") || s.contains("usd") || s.contains("dollar")) {
            return "USD";
        }
        if (s.contains("£") || s.contains("gbp") || s.contains("pound") || s.contains("pond") || s.contains("livre")) {
            return "GBP";
        }
        return null;
    }

    /** Parses "€12.50", "12,50 euro", "12.5", "$8" → amount (2 decimals) + currency if a symbol/word is present. */
    public static Money money(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher m = NUMBER.matcher(raw.replace(" ", ""));
        if (!m.find()) {
            return null;
        }
        String whole = m.group(1);
        String frac = m.group(2);
        BigDecimal amount = new BigDecimal(whole + (frac != null ? "." + frac : "")).setScale(2, RoundingMode.HALF_UP);
        return new Money(amount, currency(raw));
    }
}

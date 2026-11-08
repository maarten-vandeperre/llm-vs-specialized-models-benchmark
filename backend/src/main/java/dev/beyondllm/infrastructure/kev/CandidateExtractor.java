package dev.beyondllm.infrastructure.kev;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, non-ML candidate generation for the Kev candidate-selection experiment.
 * Kev cannot generate strings, so order-id and money candidates are proposed here and Kev only chooses.
 * This stage is timed separately and included in end-to-end latency.
 */
public final class CandidateExtractor {

    private static final Pattern TOKEN = Pattern.compile("#?[A-Za-z0-9]+(?:[-/][A-Za-z0-9]+)*");
    private static final Pattern MONEY = Pattern.compile(
            "(?:[€$£]\\s?\\d{1,4}(?:[.,]\\d{1,2})?)|(?:\\d{1,4}(?:[.,]\\d{1,2})?\\s?(?:€|EUR|eur|euros?|USD|dollars?|GBP|pounds?)\\b)");

    private CandidateExtractor() {
    }

    public record Candidates(List<String> orderIds, List<String> amounts) {
    }

    public static Candidates extract(String text) {
        Set<String> ids = new LinkedHashSet<>();
        Matcher m = TOKEN.matcher(text);
        while (m.find()) {
            String raw = m.group();
            String t = raw.startsWith("#") ? raw.substring(1) : raw;
            if (looksLikeOrderId(t)) {
                ids.add(t);
            }
        }
        Set<String> money = new LinkedHashSet<>();
        Matcher mm = MONEY.matcher(text);
        while (mm.find()) {
            money.add(mm.group().trim());
        }
        return new Candidates(new ArrayList<>(ids), new ArrayList<>(money));
    }

    static boolean looksLikeOrderId(String t) {
        if (t.length() < 5 || t.length() > 24) {
            return false;
        }
        long digits = t.chars().filter(Character::isDigit).count();
        boolean hasLetter = t.chars().anyMatch(Character::isLetter);
        boolean hasSep = t.indexOf('-') >= 0 || t.indexOf('/') >= 0;
        if (digits < 3) {
            return false;
        }
        if (!hasLetter && !hasSep) {
            return digits >= 5; // plain numeric ids like 551203
        }
        return true;
    }
}

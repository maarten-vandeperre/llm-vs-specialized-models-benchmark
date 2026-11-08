package dev.beyondllm.domain;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Fictional coffee-shop catalog with localized surface forms. Used by the generator (to write text) and by the
 * evaluator (to map any model's product string to a canonical id). The same lexicon applies to every model.
 */
public final class ProductCatalog {

    public record Product(String id, BigDecimal price, Map<String, String[]> forms) {
        /** forms: language → {singular, plural} */
        public String singular(String lang) {
            return forms.getOrDefault(lang, forms.get("en"))[0];
        }

        public String plural(String lang) {
            return forms.getOrDefault(lang, forms.get("en"))[1];
        }
    }

    private static final Map<String, Product> PRODUCTS = new LinkedHashMap<>();
    private static final Map<String, String> SURFACE_TO_ID = new HashMap<>();

    static {
        add("espresso", "2.40", f("espresso", "espressos"), f("espresso", "espresso's"), f("espresso", "espressos"));
        add("cappuccino", "3.50", f("cappuccino", "cappuccinos"), f("cappuccino", "cappuccino's"), f("cappuccino", "cappuccinos"));
        add("latte", "3.80", f("latte", "lattes"), f("latte", "lattes"), f("café latte", "cafés latte"));
        add("flat_white", "3.90", f("flat white", "flat whites"), f("flat white", "flat whites"), f("flat white", "flat whites"));
        add("americano", "2.90", f("americano", "americanos"), f("americano", "americano's"), f("americano", "americanos"));
        add("mocha", "4.20", f("mocha", "mochas"), f("mokka", "mokka's"), f("moka", "mokas"));
        add("chai_latte", "4.00", f("chai latte", "chai lattes"), f("chai latte", "chai lattes"), f("chai latte", "chai lattes"));
        add("hot_chocolate", "3.60", f("hot chocolate", "hot chocolates"), f("warme chocolademelk", "warme chocolademelken"), f("chocolat chaud", "chocolats chauds"));
        add("croissant", "2.50", f("croissant", "croissants"), f("croissant", "croissants"), f("croissant", "croissants"));
        add("blueberry_muffin", "3.10", f("blueberry muffin", "blueberry muffins"), f("bosbessenmuffin", "bosbessenmuffins"), f("muffin aux myrtilles", "muffins aux myrtilles"));
        add("iced_latte", "4.30", f("iced latte", "iced lattes"), f("ijslatte", "ijslattes"), f("latte glacé", "lattes glacés"));
        add("cheesecake", "4.80", f("cheesecake", "cheesecakes"), f("cheesecake", "cheesecakes"), f("cheesecake", "cheesecakes"));
        // extra accepted aliases (common spelling variants / generic phrasing)
        alias("cafe latte", "latte");
        alias("caffe latte", "latte");
        alias("flatwhite", "flat_white");
        alias("capuccino", "cappuccino");
        alias("cappucino", "cappuccino");
        alias("muffin", "blueberry_muffin");
        alias("muffins", "blueberry_muffin");
        alias("chocolademelk", "hot_chocolate");
        alias("chocolat", "hot_chocolate");
        alias("chai", "chai_latte");
        alias("iced lattes", "iced_latte");
        alias("moka", "mocha");
        alias("mokka", "mocha");
    }

    private ProductCatalog() {
    }

    private static String[] f(String singular, String plural) {
        return new String[]{singular, plural};
    }

    private static void add(String id, String price, String[] en, String[] nl, String[] fr) {
        Map<String, String[]> forms = new LinkedHashMap<>();
        forms.put("en", en);
        forms.put("nl", nl);
        forms.put("fr", fr);
        PRODUCTS.put(id, new Product(id, new BigDecimal(price), forms));
        SURFACE_TO_ID.put(key(id.replace('_', ' ')), id);
        for (String[] pair : forms.values()) {
            for (String s : pair) {
                SURFACE_TO_ID.put(key(s), id);
            }
        }
    }

    private static void alias(String surface, String id) {
        SURFACE_TO_ID.put(key(surface), id);
    }

    public static List<Product> all() {
        return new ArrayList<>(PRODUCTS.values());
    }

    public static Product get(String id) {
        Product p = PRODUCTS.get(id);
        if (p == null) {
            throw new IllegalArgumentException("Unknown product " + id);
        }
        return p;
    }

    /** Maps a model-produced product string to a canonical id, tolerant to case, accents, plural 's' and leading articles. */
    public static Optional<String> canonicalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String k = key(raw);
        k = k.replaceFirst("^(a|an|the|een|de|het|un|une|le|la|les|des)\\s+", "");
        String hit = SURFACE_TO_ID.get(k);
        if (hit == null && k.endsWith("s")) {
            hit = SURFACE_TO_ID.get(k.substring(0, k.length() - 1));
        }
        if (hit == null && k.endsWith("'s")) {
            hit = SURFACE_TO_ID.get(k.substring(0, k.length() - 2));
        }
        return Optional.ofNullable(hit);
    }

    /** True if any surface form of the product occurs in the text (used for hallucination detection). */
    public static boolean mentionedIn(String productId, String text) {
        String t = key(text);
        Product p = PRODUCTS.get(productId);
        if (p == null) {
            return false;
        }
        if (t.contains(key(productId.replace('_', ' ')))) {
            return true;
        }
        for (String[] pair : p.forms().values()) {
            for (String s : pair) {
                if (t.contains(key(s))) {
                    return true;
                }
            }
        }
        for (Map.Entry<String, String> e : SURFACE_TO_ID.entrySet()) {
            if (e.getValue().equals(productId) && t.contains(e.getKey())) {
                return true;
            }
        }
        return false;
    }

    static String key(String s) {
        String n = Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.replaceAll("[_\\-]", " ").replaceAll("\\s+", " ");
    }
}

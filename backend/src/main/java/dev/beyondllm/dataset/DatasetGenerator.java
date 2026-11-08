package dev.beyondllm.dataset;

import dev.beyondllm.domain.DecisionPolicy;
import dev.beyondllm.domain.LabelSchema;
import dev.beyondllm.domain.ProductCatalog;
import dev.beyondllm.domain.TaskType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Deterministic synthetic dataset generator for a fictional coffee shop ("coffee-v1").
 * <p>
 * Ground truth is assigned by explicit generation rules:
 * <ul>
 *   <li>intent/complaint are sampled first (the latent "situation"), then text is rendered from aligned phrase banks;</li>
 *   <li>sentiment: any complaint → negative; praise → positive; otherwise neutral (ambiguous → neutral);</li>
 *   <li>category: derived from complaint via {@link LabelSchema#categoryFor(String)};</li>
 *   <li>contradictions: the final stated request wins;</li>
 *   <li>items/total: sampled from the catalog; total = Σ price × quantity;</li>
 *   <li>decision: {@link DecisionPolicy#decide} applied to the gold values.</li>
 * </ul>
 * Same seed ⇒ byte-identical output (single {@link Random} stream, fixed iteration order).
 */
public final class DatasetGenerator {

    public static final String DATASET_NAME = "coffee-v1";
    public static final long DEFAULT_SEED = 42L;
    public static final double CALIBRATION_FRACTION = 0.30;
    public static final int[] LONG_TARGET_TOKENS = {100, 500, 2000, 5000};
    public static final String[] LONG_POSITIONS = {"start", "middle", "end"};
    /** Approximation used only to size long documents; real token counts are recorded per model response. */
    public static final double TOKENS_PER_WORD = 1.33;

    private static final String[] LANGS = {"en", "nl", "fr"};
    private static final String[] COMPLAINTS = {"temperature", "taste", "wrong_item", "missing_item", "late_delivery", "overcharged", "rude_staff"};
    private static final String[] REPLACEABLE = {"temperature", "taste", "wrong_item", "missing_item"};

    private Random rnd;
    /** When true, no random noise (typos, lowercasing) is applied – keeps multilingual triples aligned. */
    private boolean noNoise;
    private final List<DatasetRecord> out = new ArrayList<>();

    public DatasetGenerator(long seed) {
        this.rnd = new Random(seed);
    }

    /** Situation sampled before any text is written. */
    record Latent(String intent, String complaint, boolean praise, boolean ambiguous, Set<String> tags) {
    }

    public List<DatasetRecord> generate() {
        out.clear();
        for (int i = 0; i < 300; i++) {
            String lang = pickLang(0.70);
            add(classification(i, lang, latent()), splitFor());
        }
        for (int i = 0; i < 250; i++) {
            add(extraction(i, pickLang(0.70)), splitFor());
        }
        for (int i = 0; i < 150; i++) {
            add(combined("cmb", i, pickLang(0.70), latentWithComplaintBias(), rnd.nextLong(), "combined"), splitFor());
        }
        for (int i = 0; i < 96; i++) {
            add(ambiguous(i, pickLang(0.60)), splitFor());
        }
        for (int triple = 0; triple < 36; triple++) {
            String split = splitFor();
            Latent l = latentWithComplaintBias();
            long sub = rnd.nextLong();
            for (String lang : LANGS) {
                add(combined("mul-" + String.format("%03d", triple), -1, lang, l, sub, "multilingual"), split);
            }
        }
        int n = 0;
        for (int target : LONG_TARGET_TOKENS) {
            for (String pos : LONG_POSITIONS) {
                for (int k = 0; k < 8; k++) {
                    add(longDoc(n++, target, pos), splitFor());
                }
            }
        }
        return List.copyOf(out);
    }

    private void add(DatasetRecord r, String split) {
        out.add(new DatasetRecord(r.id(), r.group(), split, r.language(), r.task(), r.text(), r.approxTokens(), r.tags(), r.meta(), r.gold()));
    }

    private String splitFor() {
        return rnd.nextDouble() < CALIBRATION_FRACTION ? "calibration" : "evaluation";
    }

    private String pickLang(double englishShare) {
        double x = rnd.nextDouble();
        if (x < englishShare) {
            return "en";
        }
        return x < englishShare + (1 - englishShare) / 2 ? "nl" : "fr";
    }

    // ---- latent situations ------------------------------------------------------------------------------

    Latent latent() {
        double x = rnd.nextDouble();
        Set<String> tags = new LinkedHashSet<>();
        if (x < 0.25) {
            return new Latent("refund", pick(COMPLAINTS), false, false, tags);
        } else if (x < 0.40) {
            return new Latent("replacement", pick(REPLACEABLE), false, false, tags);
        } else if (x < 0.55) {
            return new Latent("feedback", pick(COMPLAINTS), false, false, tags);
        } else if (x < 0.70) {
            return new Latent("feedback", LabelSchema.NONE, true, false, tags);
        } else if (x < 0.82) {
            return new Latent("order_status", rnd.nextDouble() < 0.4 ? "late_delivery" : LabelSchema.NONE, false, false, tags);
        } else if (x < 0.90) {
            return new Latent("cancel_order", LabelSchema.NONE, false, false, tags);
        }
        return new Latent("product_question", LabelSchema.NONE, false, false, tags);
    }

    Latent latentWithComplaintBias() {
        double x = rnd.nextDouble();
        if (x < 0.45) {
            return new Latent("refund", pick(COMPLAINTS), false, false, new LinkedHashSet<>());
        } else if (x < 0.65) {
            return new Latent("replacement", pick(REPLACEABLE), false, false, new LinkedHashSet<>());
        } else if (x < 0.80) {
            return new Latent("order_status", rnd.nextDouble() < 0.5 ? "late_delivery" : LabelSchema.NONE, false, false, new LinkedHashSet<>());
        }
        return latent();
    }

    // ---- groups -----------------------------------------------------------------------------------------

    private DatasetRecord classification(int i, String lang, Latent l) {
        Builder b = new Builder(lang);
        List<DatasetRecord.GoldItem> items = sampleItems(1);
        String p = items.getFirst().product();
        boolean mentionId = rnd.nextDouble() < 0.35;
        String orderId = mentionId ? orderId() : null;
        if (rnd.nextDouble() < 0.25) {
            b.sentence(fill(pickArr(PhraseBank.FILLER), lang, p, null, null), "irrelevant_text");
        }
        String intent = renderSituation(b, l, lang, p);
        if (mentionId) {
            b.sentence(fill(pickArr(PhraseBank.ID_ONLY), lang, p, null, orderId), null);
        }
        return b.build(String.format("cls-%04d", i), "classification", TaskType.CLASSIFICATION,
                gold(intent, l, orderId, List.of(), null, null), Map.of());
    }

    private DatasetRecord extraction(int i, String lang) {
        Builder b = new Builder(lang);
        List<DatasetRecord.GoldItem> items = sampleItems(1 + rnd.nextInt(3));
        boolean mentionId = rnd.nextDouble() < 0.85;
        boolean mentionTotal = rnd.nextDouble() < 0.85;
        String orderId = mentionId ? orderId() : null;
        String currency = currency();
        BigDecimal total = total(items);
        if (rnd.nextDouble() < 0.15) {
            b.sentence(fill(pickArr(PhraseBank.FILLER), lang, null, null, null), "irrelevant_text");
        }
        String itemsText = renderItems(items, lang, b);
        String orderSentence = mentionId
                ? fill(pickArr(PhraseBank.ORDER_WITH_ID), lang, null, null, orderId).replace("{items}", itemsText)
                : fill(pickArr(PhraseBank.ORDER_NO_ID), lang, null, null, null).replace("{items}", itemsText);
        b.sentence(orderSentence, mentionId ? null : "missing_order_id");
        if (rnd.nextDouble() < 0.20) {
            b.sentence(fill(pickArr(PhraseBank.NUMERIC_DISTRACTORS), lang, null, null, null), "numeric_distractor");
        }
        if (rnd.nextDouble() < 0.12) {
            String other = otherProduct(items);
            b.sentence(fill(pickArr(PhraseBank.PRODUCT_DISTRACTORS), lang, null, other, null), "product_distractor");
        }
        String moneyText = null;
        if (mentionTotal) {
            moneyText = money(total, currency, lang);
            b.sentence(pickArr(PhraseBank.TOTAL)[PhraseBank.idx(lang)].replace("{money}", moneyText), null);
        } else {
            b.tag("missing_total");
        }
        if (rnd.nextDouble() < 0.5) {
            b.shuffleOrder(rnd);
        }
        Latent l = new Latent("feedback", LabelSchema.NONE, false, false, Set.of());
        return b.build(String.format("ext-%04d", i), "extraction", TaskType.EXTRACTION,
                gold("feedback", l, orderId, items, mentionTotal ? total : null, mentionTotal ? currency : null), Map.of());
    }

    private DatasetRecord combined(String idPrefix, int i, String lang, Latent l, long subSeed, String group) {
        // a sub-random keeps multilingual triples identical in content across languages
        Random saved = swap(new Random(subSeed));
        noNoise = group.equals("multilingual");
        try {
            Builder b = new Builder(lang);
            List<DatasetRecord.GoldItem> items = sampleItems(1 + rnd.nextInt(2));
            String p = items.getFirst().product();
            boolean mentionId = rnd.nextDouble() < 0.90;
            boolean mentionItems = true; // explicit order line, so gold items are unambiguous
            boolean mentionTotal = rnd.nextDouble() < 0.80;
            String orderId = mentionId ? orderId() : null;
            String currency = currency();
            BigDecimal total = total(items);
            boolean idFirst = rnd.nextBoolean();
            if (mentionItems) {
                String itemsText = renderItems(items, lang, b);
                String s = mentionId
                        ? fill(pickArr(PhraseBank.ORDER_WITH_ID), lang, null, null, orderId).replace("{items}", itemsText)
                        : fill(pickArr(PhraseBank.ORDER_NO_ID), lang, null, null, null).replace("{items}", itemsText);
                b.sentence(s, null);
            } else if (mentionId && idFirst) {
                b.sentence(fill(pickArr(PhraseBank.ID_ONLY), lang, p, null, orderId), null);
            }
            String intent = renderSituation(b, l, lang, p);
            if (!mentionItems && mentionId && !idFirst) {
                b.sentence(fill(pickArr(PhraseBank.ID_ONLY), lang, p, null, orderId), null);
            }
            if (mentionTotal) {
                b.sentence(pickArr(PhraseBank.TOTAL)[PhraseBank.idx(lang)].replace("{money}", money(total, currency, lang)), null);
            }
            String id = i >= 0 ? String.format("%s-%04d", idPrefix, i) : idPrefix + "-" + lang;
            return b.build(id, group, TaskType.COMBINED,
                    gold(intent, l, orderId, mentionItems ? items : List.of(), mentionTotal ? total : null, mentionTotal ? currency : null),
                    group.equals("multilingual") ? Map.of("tripleId", idPrefix) : Map.of());
        } finally {
            swap(saved);
            noNoise = false;
        }
    }

    private DatasetRecord ambiguous(int i, String lang) {
        Builder b = new Builder(lang);
        String p = sampleItems(1).getFirst().product();
        PhraseBank.Variant v = pick(PhraseBank.AMBIGUOUS);
        b.sentence(fill(v.text(), lang, p, null, null), "ambiguous");
        boolean mentionId = rnd.nextDouble() < 0.25;
        String orderId = mentionId ? orderId() : null;
        if (mentionId) {
            b.sentence(fill(pickArr(PhraseBank.ID_ONLY), lang, p, null, orderId), null);
        }
        Latent l = new Latent(LabelSchema.NONE, LabelSchema.NONE, false, true, Set.of());
        return b.build(String.format("amb-%04d", i), "ambiguous", TaskType.CLASSIFICATION,
                gold(LabelSchema.NONE, l, orderId, List.of(), null, null), Map.of());
    }

    private DatasetRecord longDoc(int i, int targetTokens, String position) {
        String lang = "en";
        Builder core = new Builder(lang);
        List<DatasetRecord.GoldItem> items = sampleItems(1 + rnd.nextInt(2));
        String orderId = orderId();
        String currency = currency();
        BigDecimal total = total(items);
        String itemsText = renderItems(items, lang, core);
        core.sentence(fill(pickArr(PhraseBank.ORDER_WITH_ID), lang, null, null, orderId).replace("{items}", itemsText), null);
        core.sentence(pickArr(PhraseBank.TOTAL)[0].replace("{money}", money(total, currency, lang)), null);
        String coreText = core.text();
        int coreWords = coreText.split("\\s+").length;
        int fillerWords = Math.max(0, (int) Math.round(targetTokens / TOKENS_PER_WORD) - coreWords);
        List<String> filler = new ArrayList<>();
        int w = 0;
        while (w < fillerWords) {
            String s = pick(PhraseBank.LONG_FILLER);
            filler.add(s);
            w += s.split("\\s+").length;
        }
        int insertAt = switch (position) {
            case "start" -> 0;
            case "middle" -> filler.size() / 2;
            default -> filler.size();
        };
        List<String> parts = new ArrayList<>(filler);
        parts.add(insertAt, coreText);
        String text = String.join(" ", parts);
        Builder b = new Builder(lang).raw(text);
        b.tag("long_document");
        Latent l = new Latent("feedback", LabelSchema.NONE, false, false, Set.of());
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("targetTokens", targetTokens);
        meta.put("position", position);
        return b.build(String.format("long-%04d", i), "long", TaskType.EXTRACTION, gold("feedback", l, orderId, items, total, currency), meta);
    }

    // ---- rendering helpers ------------------------------------------------------------------------------

    /** Renders complaint/praise/intent sentences; returns the gold intent (final request wins on contradictions). */
    private String renderSituation(Builder b, Latent l, String lang, String product) {
        if (l.praise()) {
            PhraseBank.Variant v = pick(PhraseBank.PRAISE);
            b.sentence(typo(fill(v.text(), lang, product, null, null)), v.tag());
            return "feedback";
        }
        String intent = l.intent();
        boolean intentFirst = rnd.nextDouble() < 0.3;
        String complaintSentence = null;
        String complaintTag = null;
        if (!LabelSchema.NONE.equals(l.complaint())) {
            PhraseBank.Variant c = pick(PhraseBank.COMPLAINTS.get(l.complaint()));
            complaintSentence = typo(fill(c.text(), lang, product, otherProduct(List.of(new DatasetRecord.GoldItem(product, 1))), null));
            complaintTag = c.tag();
        }
        String intentSentence;
        String intentTag;
        if ((intent.equals("refund") || intent.equals("replacement")) && rnd.nextDouble() < 0.12) {
            PhraseBank.Variant v = intent.equals("replacement") ? pick(PhraseBank.CONTRADICTION_TO_REPLACEMENT) : pick(PhraseBank.CONTRADICTION_TO_REFUND);
            intentSentence = v.text()[PhraseBank.idx(lang)];
            intentTag = "contradictory";
        } else {
            PhraseBank.Variant v = pick(PhraseBank.INTENTS.get(intent));
            intentSentence = typo(fill(v.text(), lang, product, null, null));
            intentTag = v.tag();
        }
        if (intent.equals("feedback") && rnd.nextDouble() < 0.5) {
            intentSentence = null; // complaint without any request
            intentTag = "implicit";
        }
        if (intentFirst && intentSentence != null) {
            b.sentence(intentSentence, intentTag);
            if (complaintSentence != null) {
                b.sentence(complaintSentence, complaintTag);
            }
        } else {
            if (complaintSentence != null) {
                b.sentence(complaintSentence, complaintTag);
            }
            if (intentSentence != null) {
                b.sentence(intentSentence, intentTag);
            }
        }
        if (intentSentence == null) {
            b.tag(intentTag);
        }
        return intent;
    }

    private String renderItems(List<DatasetRecord.GoldItem> items, String lang, Builder b) {
        List<String> parts = new ArrayList<>();
        boolean words = rnd.nextBoolean();
        for (DatasetRecord.GoldItem it : items) {
            ProductCatalog.Product p = ProductCatalog.get(it.product());
            String name = it.quantity() == 1 ? p.singular(lang) : p.plural(lang);
            parts.add((words ? numberWord(it.quantity(), lang) : String.valueOf(it.quantity())) + " " + name);
        }
        if (items.size() > 1) {
            b.tag("multiple_products");
        }
        if (parts.size() == 1) {
            return parts.getFirst();
        }
        String and = switch (lang) {
            case "nl" -> " en ";
            case "fr" -> " et ";
            default -> " and ";
        };
        return String.join(", ", parts.subList(0, parts.size() - 1)) + and + parts.getLast();
    }

    static String numberWord(int n, String lang) {
        String[][] w = {
                {"one", "two", "three", "four", "five", "six"},
                {"een", "twee", "drie", "vier", "vijf", "zes"},
                {"un", "deux", "trois", "quatre", "cinq", "six"}};
        return w[PhraseBank.idx(lang)][n - 1];
    }

    static String money(BigDecimal amount, String currency, String lang) {
        String dot = amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String comma = dot.replace('.', ',');
        return switch (currency) {
            case "USD" -> lang.equals("en") ? "$" + dot : comma + (lang.equals("nl") ? " dollar" : " dollars");
            case "GBP" -> lang.equals("en") ? "£" + dot : comma + (lang.equals("nl") ? " pond" : " livres");
            default -> switch (lang) {
                case "nl" -> "€ " + comma;
                case "fr" -> comma + " €";
                default -> "€" + dot;
            };
        };
    }

    private String fill(String[] tpl, String lang, String productId, String otherId, String orderId) {
        String s = tpl[PhraseBank.idx(lang)];
        if (productId != null) {
            s = s.replace("{p}", ProductCatalog.get(productId).singular(lang));
        }
        if (otherId != null) {
            s = s.replace("{o}", ProductCatalog.get(otherId).singular(lang));
        }
        if (orderId != null) {
            String rendered = (orderId.matches("[A-Z0-9]{6}") || orderId.startsWith("ORD")) && rnd.nextBoolean() ? "#" + orderId : orderId;
            s = s.replace("{id}", rendered);
        }
        return s;
    }

    /** Introduces one spelling error (adjacent swap) in a word of ≥6 letters with probability 0.15. Never touches ids/numbers. */
    private String typo(String s) {
        if (noNoise || rnd.nextDouble() >= 0.15) {
            return s;
        }
        String[] words = s.split(" ");
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < words.length; i++) {
            if (words[i].matches("\\p{L}{6,}") && ProductCatalog.canonicalize(words[i]).isEmpty()) {
                candidates.add(i);
            }
        }
        if (candidates.isEmpty()) {
            return s;
        }
        int wi = candidates.get(rnd.nextInt(candidates.size()));
        char[] c = words[wi].toCharArray();
        int k = 1 + rnd.nextInt(c.length - 2);
        char tmp = c[k];
        c[k] = c[k + 1];
        c[k + 1] = tmp;
        words[wi] = new String(c);
        pendingTypo = true;
        return String.join(" ", words);
    }

    private boolean pendingTypo;

    private List<DatasetRecord.GoldItem> sampleItems(int n) {
        List<ProductCatalog.Product> all = new ArrayList<>(ProductCatalog.all());
        List<DatasetRecord.GoldItem> items = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ProductCatalog.Product p = all.remove(rnd.nextInt(all.size()));
            items.add(new DatasetRecord.GoldItem(p.id(), 1 + rnd.nextInt(4)));
        }
        return items;
    }

    private String otherProduct(List<DatasetRecord.GoldItem> exclude) {
        List<String> ids = new ArrayList<>();
        for (ProductCatalog.Product p : ProductCatalog.all()) {
            if (exclude.stream().noneMatch(e -> e.product().equals(p.id()))) {
                ids.add(p.id());
            }
        }
        return ids.get(rnd.nextInt(ids.size()));
    }

    static BigDecimal total(List<DatasetRecord.GoldItem> items) {
        BigDecimal t = BigDecimal.ZERO;
        for (DatasetRecord.GoldItem it : items) {
            t = t.add(ProductCatalog.get(it.product()).price().multiply(BigDecimal.valueOf(it.quantity())));
        }
        return t.setScale(2, RoundingMode.HALF_UP);
    }

    private String currency() {
        double x = rnd.nextDouble();
        return x < 0.80 ? "EUR" : x < 0.90 ? "USD" : "GBP";
    }

    private String orderId() {
        int f = rnd.nextInt(6);
        return switch (f) {
            case 0 -> String.format("BE-2026-%05d", rnd.nextInt(100000));
            case 1 -> String.format("NL-2026-%05d", rnd.nextInt(100000));
            case 2 -> String.format("FR-2026-%05d", rnd.nextInt(100000));
            case 3 -> String.format("ORD-%06d", rnd.nextInt(1000000));
            case 4 -> String.format("CB/2026/%04d", rnd.nextInt(10000));
            default -> alnumId();
        };
    }

    private String alnumId() {
        String letters = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        char[] c = new char[6];
        for (int i = 0; i < 6; i++) {
            c[i] = i % 2 == 0 ? letters.charAt(rnd.nextInt(letters.length())) : (char) ('0' + rnd.nextInt(10));
        }
        return new String(c);
    }

    private <T> T pick(List<T> l) {
        return l.get(rnd.nextInt(l.size()));
    }

    private String pick(String[] a) {
        return a[rnd.nextInt(a.length)];
    }

    private String[] pickArr(List<String[]> l) {
        return l.get(rnd.nextInt(l.size()));
    }

    private Random swap(Random r) {
        Random old = rnd;
        rnd = r;
        return old;
    }

    private DatasetRecord.Gold gold(String intent, Latent l, String orderId, List<DatasetRecord.GoldItem> items, BigDecimal total, String currency) {
        String complaint = l.complaint();
        String sentiment = l.praise() ? "positive" : !LabelSchema.NONE.equals(complaint) ? "negative" : "neutral";
        String category = LabelSchema.categoryFor(complaint);
        String decision = DecisionPolicy.decide(intent, complaint, orderId != null, total);
        return new DatasetRecord.Gold(intent, sentiment, category, complaint, orderId, null, List.copyOf(items), total, currency, null, decision);
    }

    // ---- record builder ---------------------------------------------------------------------------------

    private final class Builder {
        private final String lang;
        private final List<String> sentences = new ArrayList<>();
        private final Set<String> tags = new LinkedHashSet<>();
        private String raw;

        Builder(String lang) {
            this.lang = lang;
        }

        Builder raw(String text) {
            this.raw = text;
            return this;
        }

        void sentence(String s, String tag) {
            sentences.add(s);
            tag(tag);
            if (pendingTypo) {
                tags.add("spelling_error");
                pendingTypo = false;
            }
        }

        void tag(String tag) {
            if (tag != null && !tag.equals("explicit")) {
                tags.add(tag);
            }
        }

        void shuffleOrder(Random r) {
            java.util.Collections.shuffle(sentences, r);
        }

        String text() {
            return raw != null ? raw : String.join(" ", sentences);
        }

        DatasetRecord build(String id, String group, TaskType task, DatasetRecord.Gold g, Map<String, Object> meta) {
            String text = text();
            if (!noNoise && raw == null && rnd.nextDouble() < 0.05) {
                text = text.toLowerCase(Locale.ROOT);
                tags.add("lowercase");
            }
            DatasetRecord.Gold withSpans = DatasetRepository.withSpans(g, text);
            int approx = (int) Math.round(text.split("\\s+").length * TOKENS_PER_WORD);
            return new DatasetRecord(id, group, null, lang, task, text, approx, List.copyOf(tags), meta, withSpans);
        }
    }
}

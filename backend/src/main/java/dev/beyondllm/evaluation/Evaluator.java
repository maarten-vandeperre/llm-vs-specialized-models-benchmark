package dev.beyondllm.evaluation;

import dev.beyondllm.dataset.DatasetRecord;
import dev.beyondllm.domain.BenchmarkResponse;
import dev.beyondllm.domain.DecisionPolicy;
import dev.beyondllm.domain.Extraction;
import dev.beyondllm.domain.LabelPrediction;
import dev.beyondllm.domain.LabelSchema;
import dev.beyondllm.domain.Prediction;
import dev.beyondllm.domain.ProductCatalog;
import dev.beyondllm.domain.Span;
import dev.beyondllm.domain.TaskType;
import dev.beyondllm.domain.ValueNormalizer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Scores one response against gold. Non-successful responses are scored as wrong (never excluded):
 * every classification field is incorrect and every gold extraction tuple counts as a false negative.
 */
public final class Evaluator {

    private Evaluator() {
    }

    public static RecordEvaluation evaluate(DatasetRecord record, TaskType requestTask, BenchmarkResponse resp) {
        DatasetRecord.Gold g = record.gold();
        Prediction p = resp.isSuccess() ? resp.prediction() : null;
        String failed = resp.isSuccess() ? null : "__" + resp.outcome().name().toLowerCase(Locale.ROOT);
        boolean allCorrect = true;

        Map<String, RecordEvaluation.FieldEval> cls = null;
        if (requestTask.needsClassification()) {
            cls = new LinkedHashMap<>();
            for (String field : LabelSchema.FIELDS) {
                String gold = g.label(field);
                LabelPrediction lp = p == null ? null : p.labels().get(field);
                String pred = lp == null ? (failed != null ? failed : "__missing") : lp.label();
                boolean ok = gold.equals(pred);
                allCorrect &= ok;
                cls.put(field, new RecordEvaluation.FieldEval(gold, pred, ok, lp == null ? null : lp.probability()));
            }
        }

        RecordEvaluation.ExtractionEval ext = null;
        if (requestTask.needsExtraction()) {
            ext = evaluateExtraction(record, p == null ? null : p.extraction());
            allCorrect &= ext.exactMatch();
        }

        RecordEvaluation.DecisionEval dec = null;
        if (requestTask == TaskType.COMBINED) {
            String predicted = null;
            if (p != null && p.extraction() != null) {
                ValueNormalizer.Money m = ValueNormalizer.money(p.extraction().total());
                predicted = DecisionPolicy.decide(p.label(LabelSchema.INTENT), p.label(LabelSchema.COMPLAINT),
                        ValueNormalizer.orderId(p.extraction().orderId()) != null, m == null ? null : m.amount());
            }
            dec = new RecordEvaluation.DecisionEval(g.decision(), predicted, g.decision().equals(predicted));
        }
        return new RecordEvaluation(cls, ext, dec, allCorrect);
    }

    /** Gold extraction tuples: orderId, total, currency, and one tuple per (product, quantity). */
    static Set<String> goldTuples(DatasetRecord.Gold g) {
        Set<String> t = new LinkedHashSet<>();
        if (g.orderId() != null) {
            t.add("orderId=" + ValueNormalizer.orderId(g.orderId()));
        }
        if (g.total() != null) {
            t.add("total=" + g.total().setScale(2, RoundingMode.HALF_UP).toPlainString());
        }
        if (g.currency() != null) {
            t.add("currency=" + g.currency());
        }
        for (DatasetRecord.GoldItem it : g.items()) {
            t.add("item=" + it.product() + "x" + it.quantity());
        }
        return t;
    }

    record Normalized(String orderId, BigDecimal total, String currency, Map<String, Integer> items, List<String> unknownProducts) {
    }

    static Normalized normalize(Extraction e) {
        if (e == null) {
            return new Normalized(null, null, null, Map.of(), List.of());
        }
        ValueNormalizer.Money money = ValueNormalizer.money(e.total());
        String currency = ValueNormalizer.currency(e.currency());
        if (currency == null && money != null) {
            currency = money.currency();
        }
        Map<String, Integer> items = new LinkedHashMap<>();
        List<String> unknown = new ArrayList<>();
        for (Extraction.LineItem li : e.items()) {
            Integer q = ValueNormalizer.quantity(li.quantity());
            var id = ProductCatalog.canonicalize(li.product());
            if (id.isEmpty()) {
                unknown.add(li.product());
                continue;
            }
            items.merge(id.get(), q == null ? 0 : q, Math::max); // repeated mentions of the same product: keep max quantity
        }
        return new Normalized(ValueNormalizer.orderId(e.orderId()), money == null ? null : money.amount(), currency, items, unknown);
    }

    static RecordEvaluation.ExtractionEval evaluateExtraction(DatasetRecord record, Extraction e) {
        DatasetRecord.Gold g = record.gold();
        String text = record.text();
        Normalized n = normalize(e);
        Set<String> gold = goldTuples(g);
        Set<String> pred = new LinkedHashSet<>();
        if (n.orderId() != null) {
            pred.add("orderId=" + n.orderId());
        }
        if (n.total() != null) {
            pred.add("total=" + n.total().toPlainString());
        }
        if (n.currency() != null) {
            pred.add("currency=" + n.currency());
        }
        n.items().forEach((k, v) -> pred.add("item=" + k + "x" + v));
        n.unknownProducts().forEach(u -> pred.add("item=?" + u));

        int tp = 0;
        for (String s : pred) {
            if (gold.contains(s)) {
                tp++;
            }
        }
        int fp = pred.size() - tp;
        int fn = gold.size() - tp;

        List<String> hallucinated = new ArrayList<>();
        String lower = text.toLowerCase(Locale.ROOT);
        if (n.orderId() != null && !lower.contains(n.orderId().toLowerCase(Locale.ROOT))) {
            hallucinated.add("orderId=" + n.orderId());
        }
        if (n.total() != null) {
            String dot = n.total().toPlainString();
            String noTrailing = n.total().stripTrailingZeros().toPlainString();
            if (!text.contains(dot) && !text.contains(dot.replace('.', ',')) && !containsNumber(text, noTrailing)) {
                hallucinated.add("total=" + dot);
            }
        }
        for (String product : n.items().keySet()) {
            if (!ProductCatalog.mentionedIn(product, text)) {
                hallucinated.add("item=" + product);
            }
        }
        for (String u : n.unknownProducts()) {
            if (!lower.contains(u.toLowerCase(Locale.ROOT))) {
                hallucinated.add("item=?" + u);
            }
        }

        List<String> missing = new ArrayList<>();
        if (g.orderId() != null && n.orderId() == null) {
            missing.add("orderId");
        }
        if (g.total() != null && n.total() == null) {
            missing.add("total");
        }
        if (g.currency() != null && n.currency() == null) {
            missing.add("currency");
        }
        for (DatasetRecord.GoldItem it : g.items()) {
            if (!n.items().containsKey(it.product())) {
                missing.add("item=" + it.product());
            }
        }

        Map<String, Boolean> fields = new LinkedHashMap<>();
        fields.put("orderId", eq(g.orderId() == null ? null : ValueNormalizer.orderId(g.orderId()), n.orderId()));
        fields.put("total", g.total() == null ? n.total() == null : n.total() != null && g.total().compareTo(n.total()) == 0);
        fields.put("currency", eq(g.currency(), n.currency()));
        Map<String, Integer> goldItems = new LinkedHashMap<>();
        g.items().forEach(i -> goldItems.put(i.product(), i.quantity()));
        fields.put("items", goldItems.equals(n.items()) && n.unknownProducts().isEmpty());
        boolean exact = fields.values().stream().allMatch(Boolean::booleanValue);

        Boolean offset = null;
        if (e != null && e.orderIdSpan() != null && e.orderIdSpan().start() >= 0 && g.orderIdSpan() != null) {
            Span s = e.orderIdSpan();
            boolean inBounds = s.end() <= text.length() && s.start() < s.end();
            boolean textMatches = inBounds && eq(ValueNormalizer.orderId(text.substring(s.start(), s.end())), ValueNormalizer.orderId(g.orderId()));
            boolean overlaps = s.start() < g.orderIdSpan().end() && g.orderIdSpan().start() < s.end();
            offset = textMatches && overlaps;
        }
        return new RecordEvaluation.ExtractionEval(exact, tp, fp, fn, hallucinated, missing, fields, offset, pred.size());
    }

    private static boolean containsNumber(String text, String number) {
        return text.matches("(?s).*(?<![\\d.,])" + java.util.regex.Pattern.quote(number) + "(?![\\d]|[.,]\\d).*")
                || text.matches("(?s).*(?<![\\d.,])" + java.util.regex.Pattern.quote(number.replace('.', ',')) + "(?![\\d]|[.,]\\d).*");
    }

    private static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }
}

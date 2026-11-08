package dev.beyondllm.evaluation;

import dev.beyondllm.dataset.DatasetRecord;
import dev.beyondllm.dataset.DatasetRepository;
import dev.beyondllm.domain.BenchmarkRequest;
import dev.beyondllm.domain.BenchmarkResponse;
import dev.beyondllm.domain.Extraction;
import dev.beyondllm.domain.LabelPrediction;
import dev.beyondllm.domain.Outcome;
import dev.beyondllm.domain.Prediction;
import dev.beyondllm.domain.ProductCatalog;
import dev.beyondllm.domain.Span;
import dev.beyondllm.domain.TaskType;
import dev.beyondllm.domain.Timing;
import dev.beyondllm.domain.ValueNormalizer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluatorTest {

    static final String TEXT = "Order BE-2026-84921 contains two cappuccinos and one latte. The total was €12.50. It was cold, refund please.";

    static DatasetRecord record() {
        DatasetRecord.Gold g = new DatasetRecord.Gold("refund", "negative", "product_quality", "temperature", "BE-2026-84921", null,
                List.of(new DatasetRecord.GoldItem("cappuccino", 2), new DatasetRecord.GoldItem("latte", 1)), new BigDecimal("12.50"), "EUR", null,
                "auto_refund");
        return new DatasetRecord("t1", "combined", "evaluation", "en", TaskType.COMBINED, TEXT, 20, List.of(), Map.of(),
                DatasetRepository.withSpans(g, TEXT));
    }

    static Map<String, LabelPrediction> labels(String intent) {
        return Map.of("intent", LabelPrediction.of(intent), "sentiment", LabelPrediction.of("negative"),
                "category", LabelPrediction.of("product_quality"), "complaint", LabelPrediction.of("temperature"));
    }

    static BenchmarkResponse ok(Prediction p) {
        return BenchmarkResponse.success("m", new BenchmarkRequest("t1", TaskType.COMBINED, TEXT, "en"), p, Timing.client(1), "{}", Map.of());
    }

    @Test
    void exactMatchWithNormalizationAcrossSurfaceForms() {
        Extraction e = new Extraction("#BE-2026-84921", new Span(6, 19, "BE-2026-84921", 0.9),
                List.of(new Extraction.LineItem("cappuccinos", "two", null), new Extraction.LineItem("Latte", null, null)), "€12.50", null, null);
        RecordEvaluation ev = Evaluator.evaluate(record(), TaskType.COMBINED, ok(Prediction.of(labels("refund"), e)));
        assertTrue(ev.extraction().exactMatch());
        assertEquals(5, ev.extraction().tp());
        assertEquals(0, ev.extraction().fp());
        assertTrue(ev.extraction().offsetCorrect());
        assertEquals("auto_refund", ev.decision().predicted());
        assertTrue(ev.correct());
    }

    @Test
    void hallucinationsAndMissingValuesAreCounted() {
        Extraction e = new Extraction("BE-9999", null, List.of(new Extraction.LineItem("mocha", "1", null)), null, null, "EUR");
        RecordEvaluation ev = Evaluator.evaluate(record(), TaskType.EXTRACTION, ok(Prediction.of(Map.of(), e)));
        assertFalse(ev.extraction().exactMatch());
        assertTrue(ev.extraction().hallucinated().contains("orderId=BE-9999"));
        assertTrue(ev.extraction().hallucinated().contains("item=mocha"));
        assertTrue(ev.extraction().missing().contains("total"));
        assertEquals(1, ev.extraction().tp(), "currency only");
    }

    @Test
    void failuresAreScoredAsErrorsNotExcluded() {
        BenchmarkResponse timeout = BenchmarkResponse.failure("m", new BenchmarkRequest("t1", TaskType.COMBINED, TEXT, "en"), Outcome.TIMEOUT, "t", null, null);
        RecordEvaluation ev = Evaluator.evaluate(record(), TaskType.COMBINED, timeout);
        assertFalse(ev.correct());
        assertEquals("__timeout", ev.classification().get("intent").predicted());
        assertEquals(5, ev.extraction().fn());
        assertFalse(ev.decision().correct());
    }

    @Test
    void normalizers() {
        assertEquals(new BigDecimal("12.50"), ValueNormalizer.money("12,50 €").amount());
        assertEquals("EUR", ValueNormalizer.money("€ 12,50").currency());
        assertEquals("GBP", ValueNormalizer.currency("7,20 livres"));
        assertEquals(3, ValueNormalizer.quantity("drie"));
        assertEquals(2, ValueNormalizer.quantity("2x"));
        assertEquals(1, ValueNormalizer.quantity(null), "implicit singular");
        assertEquals("A7K29Q", ValueNormalizer.orderId("#a7k29q."));
        assertEquals("hot_chocolate", ProductCatalog.canonicalize("chocolats chauds").orElseThrow());
        assertEquals("blueberry_muffin", ProductCatalog.canonicalize("bosbessenmuffins").orElseThrow());
        assertEquals("latte", ProductCatalog.canonicalize("the lattes").orElseThrow());
    }
}

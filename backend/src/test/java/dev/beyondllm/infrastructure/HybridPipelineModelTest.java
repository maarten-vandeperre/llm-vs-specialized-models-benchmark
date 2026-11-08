package dev.beyondllm.infrastructure;

import dev.beyondllm.application.BenchmarkModel;
import dev.beyondllm.domain.BenchmarkRequest;
import dev.beyondllm.domain.BenchmarkResponse;
import dev.beyondllm.domain.Extraction;
import dev.beyondllm.domain.LabelPrediction;
import dev.beyondllm.domain.Outcome;
import dev.beyondllm.domain.Prediction;
import dev.beyondllm.domain.TaskType;
import dev.beyondllm.domain.Timing;
import dev.beyondllm.infrastructure.hybrid.HybridPipelineModel;
import dev.beyondllm.infrastructure.hybrid.HybridThresholds;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HybridPipelineModelTest {

    static Map<String, LabelPrediction> labels(String intent, double p) {
        return Map.of("intent", new LabelPrediction(intent, p, null, null), "sentiment", new LabelPrediction("negative", p, null, null),
                "category", new LabelPrediction("product_quality", p, null, null), "complaint", new LabelPrediction("temperature", p, null, null));
    }

    static BenchmarkModel stub(String id, Map<String, LabelPrediction> labels, Extraction ex) {
        BenchmarkModel m = mock(BenchmarkModel.class);
        when(m.id()).thenReturn(id);
        when(m.runtimeModels()).thenReturn(List.of());
        when(m.execute(any())).thenAnswer(inv -> {
            BenchmarkRequest r = inv.getArgument(0);
            return BenchmarkResponse.success(id, r, Prediction.of(labels, ex), Timing.client(1000), "{}", Map.of());
        });
        return m;
    }

    private final HybridThresholds thresholds = new HybridThresholds(0.8, 0.8, "target/none");
    private final Extraction ex = new Extraction("BE-1", null, List.of(new Extraction.LineItem("latte", "1", null)), "€3.80", null, null);

    @Test
    void confidentKevAvoidsFallbackAndDecides() {
        BenchmarkModel gliner = stub("gliner", Map.of(), ex);
        BenchmarkModel kev = stub("kev", labels("refund", 0.95), null);
        BenchmarkModel qwen = stub("qwen", labels("feedback", 1), ex);
        HybridPipelineModel h = new HybridPipelineModel("h", HybridPipelineModel.Strategy.GLINER_KEV_FALLBACK, gliner, kev, qwen, thresholds);
        BenchmarkResponse r = h.execute(new BenchmarkRequest("r", TaskType.COMBINED, "x", "en"));
        assertEquals(Outcome.SUCCESS, r.outcome());
        assertEquals("refund", r.prediction().label("intent"));
        assertEquals("auto_refund", r.prediction().decision());
        assertEquals(List.of("gliner", "kev"), r.prediction().route());
        assertEquals(false, r.metadata().get("fallbackUsed"));
        verify(qwen, never()).execute(any());
        assertTrue(r.timing().stageNanos().containsKey("gliner") && r.timing().stageNanos().containsKey("kev"));
    }

    @Test
    void lowConfidenceRoutesToFallback() {
        BenchmarkModel gliner = stub("gliner", Map.of(), ex);
        BenchmarkModel kev = stub("kev", labels("refund", 0.4), null);
        BenchmarkModel qwen = stub("qwen", labels("replacement", 1), ex);
        HybridPipelineModel h = new HybridPipelineModel("h", HybridPipelineModel.Strategy.GLINER_KEV_FALLBACK, gliner, kev, qwen, thresholds);
        BenchmarkResponse r = h.execute(new BenchmarkRequest("r", TaskType.COMBINED, "x", "en"));
        assertEquals("replacement", r.prediction().label("intent"));
        assertEquals(true, r.metadata().get("fallbackUsed"));
        assertEquals(List.of("gliner", "kev", "qwen (fallback)"), r.prediction().route());
    }

    @Test
    void combinedStrategyFallsBackWhenGlinerFails() {
        BenchmarkModel gliner = mock(BenchmarkModel.class);
        when(gliner.id()).thenReturn("gliner");
        when(gliner.execute(any())).thenAnswer(inv -> BenchmarkResponse.failure("gliner", inv.getArgument(0), Outcome.UNSUPPORTED, "too long", null, null));
        BenchmarkModel qwen = stub("qwen", labels("refund", 1), ex);
        HybridPipelineModel h = new HybridPipelineModel("h", HybridPipelineModel.Strategy.GLINER_COMBINED_FALLBACK, gliner, null, qwen, thresholds);
        BenchmarkResponse r = h.execute(new BenchmarkRequest("r", TaskType.COMBINED, "x", "en"));
        assertEquals(Outcome.SUCCESS, r.outcome());
        assertEquals("BE-1", r.prediction().extraction().orderId());
    }

    @Test
    void withoutFallbackAStageFailureIsPropagated() {
        BenchmarkModel gliner = stub("gliner", Map.of(), ex);
        BenchmarkModel kev = mock(BenchmarkModel.class);
        when(kev.id()).thenReturn("kev");
        when(kev.execute(any())).thenAnswer(inv -> BenchmarkResponse.failure("kev", inv.getArgument(0), Outcome.TIMEOUT, "slow", null, null));
        HybridPipelineModel h = new HybridPipelineModel("h", HybridPipelineModel.Strategy.GLINER_KEV_FALLBACK, gliner, kev, null, thresholds);
        BenchmarkResponse r = h.execute(new BenchmarkRequest("r", TaskType.COMBINED, "x", "en"));
        assertEquals(Outcome.TIMEOUT, r.outcome());
    }
}

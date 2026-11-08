package dev.beyondllm.live;

import dev.beyondllm.application.BenchmarkModel;
import dev.beyondllm.domain.BenchmarkRequest;
import dev.beyondllm.domain.BenchmarkResponse;
import dev.beyondllm.domain.Outcome;
import dev.beyondllm.domain.TaskType;
import dev.beyondllm.infrastructure.gliner.GlinerModel;
import dev.beyondllm.infrastructure.kev.KevModel;
import dev.beyondllm.infrastructure.nuextract.NuExtractModel;
import dev.beyondllm.infrastructure.ollama.OllamaAdminClient;
import dev.beyondllm.infrastructure.qwen.QwenModel;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Live integration tests against real local endpoints. Excluded from ordinary builds; run with
 * {@code mvn verify -Plive}. Each test is skipped (not failed) when its runtime/model is not available.
 */
@Tag("live")
class LiveModelsTest {

    static final String OLLAMA = System.getProperty("ollama.url", "http://localhost:11434");
    static final String GLINER = System.getProperty("gliner.url", "http://localhost:8090");
    static final String TEXT = "Order BE-2026-84921 arrived cold and I would like my money back. It was two lattes, €7.60.";
    static final Duration T = Duration.ofSeconds(180);

    private static BenchmarkResponse runIfUp(BenchmarkModel m, TaskType task) {
        Assumptions.assumeTrue(m.health().isUp(), () -> m.id() + " not available: " + m.health().detail());
        return m.execute(new BenchmarkRequest("live", task, TEXT, "en"));
    }

    @Test
    void kev() {
        BenchmarkResponse r = runIfUp(new KevModel("kev-0.8b", "kev:0.8b-mlx-bf16", false, new OllamaAdminClient(OLLAMA), T, "5m"), TaskType.CLASSIFICATION);
        assertEquals(Outcome.SUCCESS, r.outcome(), r.error());
        assertEquals("refund", r.prediction().label("intent"));
    }

    @Test
    void gliner() {
        BenchmarkResponse r = runIfUp(new GlinerModel("gliner2.5-base", "fastino/gliner2.5-base-v1", "GLiNER", EnumSet.allOf(TaskType.class), false,
                GLINER, T, 0.5), TaskType.COMBINED);
        assertEquals(Outcome.SUCCESS, r.outcome(), r.error());
        assertEquals("BE-2026-84921", r.prediction().extraction().orderId());
    }

    @Test
    void nuextract() {
        BenchmarkResponse r = runIfUp(new NuExtractModel("nuextract3", "numind/nuextract3:q4_k_m", new OllamaAdminClient(OLLAMA), T, 8192, "5m", false),
                TaskType.EXTRACTION);
        assertEquals(Outcome.SUCCESS, r.outcome(), r.error());
        assertEquals("BE-2026-84921", r.prediction().extraction().orderId());
    }

    @Test
    void qwen() {
        BenchmarkResponse r = runIfUp(new QwenModel("qwen3.5-4b", "qwen3.5:4b", new OllamaAdminClient(OLLAMA), T, 8192, false), TaskType.COMBINED);
        assertEquals(Outcome.SUCCESS, r.outcome(), r.error());
        assertEquals("BE-2026-84921", r.prediction().extraction().orderId());
    }
}

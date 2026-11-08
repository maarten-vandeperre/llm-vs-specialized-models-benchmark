package dev.beyondllm.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import dev.beyondllm.domain.BenchmarkRequest;
import dev.beyondllm.domain.BenchmarkResponse;
import dev.beyondllm.domain.Extraction;
import dev.beyondllm.domain.Outcome;
import dev.beyondllm.domain.TaskType;
import dev.beyondllm.infrastructure.gliner.GlinerModel;
import dev.beyondllm.infrastructure.http.JsonHttp;
import dev.beyondllm.support.StubServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlinerModelTest {

    private GlinerModel model(StubServer s, EnumSet<TaskType> tasks) {
        return new GlinerModel("gliner", "fastino/gliner2.5-base-v1", "GLiNER", tasks, false, s.url(), Duration.ofSeconds(5), 0.5);
    }

    @Test
    void combinedCallParsesLabelsEntitiesOffsetsAndPairsQuantities() throws Exception {
        try (StubServer s = new StubServer().on("/v1/extract", 200, StubServer.resource("gliner-combined.json"))) {
            BenchmarkResponse r = model(s, EnumSet.allOf(TaskType.class)).execute(new BenchmarkRequest("r", TaskType.COMBINED, "x", "en"));
            assertEquals(Outcome.SUCCESS, r.outcome());
            assertEquals("refund", r.prediction().label("intent"));
            assertEquals(0.9999955892562866, r.prediction().labels().get("intent").probability(), 1e-12);
            assertNull(r.prediction().labels().get("intent").distribution(), "GLiNER exposes no full distribution");
            Extraction e = r.prediction().extraction();
            assertEquals("BE-2026-84921", e.orderId());
            assertEquals(6, e.orderIdSpan().start());
            assertEquals("Two", e.items().getFirst().quantity());
            assertEquals("€7.60", e.total());
            assertEquals(142_978_208L, r.timing().serverNanos(), 1_000);
            JsonNode req = JsonHttp.MAPPER.readTree(s.lastRequest());
            assertTrue(req.has("entities") && req.has("classifications"), "single multi-task call");
            assertEquals(4, req.path("classifications").size());
        }
    }

    @Test
    void pairingPrefersQuantityPrecedingAProduct() {
        String ents = """
                {"product":[{"text":"bosbessenmuffin","start":16,"end":31,"confidence":0.9},
                            {"text":"chai lattes","start":38,"end":49,"confidence":0.9}],
                 "quantity":[{"text":"drie","start":33,"end":37,"confidence":0.9}]}""";
        try {
            Extraction e = invokeParse(JsonHttp.MAPPER.readTree(ents));
            assertNull(e.items().get(0).quantity(), "muffin must not steal the quantity of the next product");
            assertEquals("drie", e.items().get(1).quantity());
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private static Extraction invokeParse(JsonNode n) throws Exception {
        var m = GlinerModel.class.getDeclaredMethod("parseEntities", JsonNode.class);
        m.setAccessible(true);
        return (Extraction) m.invoke(null, n);
    }

    @Test
    void tooLongInputIsUnsupportedNotTruncated() {
        try (StubServer s = new StubServer().on("/v1/extract", 413, "{\"error\":\"input_too_long\",\"input_tokens\":6000,\"max_tokens\":4096}")) {
            BenchmarkResponse r = model(s, EnumSet.allOf(TaskType.class)).execute(new BenchmarkRequest("r", TaskType.COMBINED, "x", "en"));
            assertEquals(Outcome.UNSUPPORTED, r.outcome());
            assertTrue(r.error().contains("input_too_long"));
        }
    }

    @Test
    void decideCheckpointRejectsExtraction() {
        try (StubServer s = new StubServer()) {
            assertEquals(Outcome.UNSUPPORTED, model(s, EnumSet.of(TaskType.CLASSIFICATION))
                    .execute(new BenchmarkRequest("r", TaskType.EXTRACTION, "x", "en")).outcome());
        }
    }

    @Test
    void serviceDownIsInfraError() {
        GlinerModel m = new GlinerModel("g", "c", "G", EnumSet.allOf(TaskType.class), false, "http://127.0.0.1:1", Duration.ofSeconds(1), 0.5);
        assertEquals(Outcome.INFRA_ERROR, m.execute(new BenchmarkRequest("r", TaskType.CLASSIFICATION, "x", "en")).outcome());
        assertEquals(dev.beyondllm.domain.HealthStatus.State.DOWN, m.health().state());
    }
}

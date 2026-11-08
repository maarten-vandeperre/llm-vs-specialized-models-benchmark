package dev.beyondllm.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import dev.beyondllm.domain.BenchmarkRequest;
import dev.beyondllm.domain.BenchmarkResponse;
import dev.beyondllm.domain.HealthStatus;
import dev.beyondllm.domain.Outcome;
import dev.beyondllm.domain.TaskType;
import dev.beyondllm.infrastructure.http.JsonHttp;
import dev.beyondllm.infrastructure.kev.CandidateExtractor;
import dev.beyondllm.infrastructure.kev.KevModel;
import dev.beyondllm.infrastructure.ollama.OllamaAdminClient;
import dev.beyondllm.support.StubServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KevModelTest {

    private KevModel kev(StubServer s, boolean candsel, Duration timeout) {
        return new KevModel(candsel ? "kev-candsel" : "kev", "kev:0.8b-mlx-bf16", candsel, new OllamaAdminClient(s.url()), timeout, "5m");
    }

    @Test
    void sendsTypedSystemOneRequestAndParsesDistributions() throws Exception {
        try (StubServer s = new StubServer().on("/v1/systemone", 200, StubServer.resource("kev-classification.json"))) {
            BenchmarkResponse r = kev(s, false, Duration.ofSeconds(5))
                    .execute(new BenchmarkRequest("r1", TaskType.CLASSIFICATION, "My cappuccino arrived cold and I want a refund.", "en"));

            assertEquals(Outcome.SUCCESS, r.outcome());
            assertEquals("refund", r.prediction().label("intent"));
            assertEquals(0.8451, r.prediction().labels().get("intent").probability(), 1e-9);
            assertEquals(0.7676, r.prediction().labels().get("intent").providerConfidence(), 1e-9, "Kev confidence kept separately");
            assertEquals(7, r.prediction().labels().get("intent").distribution().size());
            assertEquals(341, r.timing().inputTokens());
            assertNull(r.timing().serverNanos(), "System One returns no durations – must not be invented");

            JsonNode req = JsonHttp.MAPPER.readTree(s.lastRequest());
            assertEquals("kev:0.8b-mlx-bf16", req.get("model").asText());
            assertEquals("My cappuccino arrived cold and I want a refund.", req.path("state").path("customer_message").asText());
            assertEquals(4, req.get("questions").size(), "all four questions in one request");
            assertEquals("choice", req.path("questions").path("intent").path("type").asText());
            assertTrue(req.path("questions").path("intent").path("criteria").has("none"), "explicit none option for abstention");
        }
    }

    @Test
    void extractionIsUnsupportedForPlainKev() {
        try (StubServer s = new StubServer()) {
            BenchmarkResponse r = kev(s, false, Duration.ofSeconds(5)).execute(new BenchmarkRequest("r", TaskType.EXTRACTION, "Order X", "en"));
            assertEquals(Outcome.UNSUPPORTED, r.outcome());
            assertTrue(s.requests.isEmpty(), "never sent to the model");
        }
    }

    @Test
    void candidateSelectionAsksOnlyAboutRegexCandidates() throws Exception {
        String answer = """
                {"model":"kev","answers":{
                  "order_id":{"type":"choice","choice":"c1","probabilities":{"c1":0.9,"none":0.1},"confidence":0.5},
                  "total":{"type":"choice","choice":"a1","probabilities":{"a1":0.8,"none":0.2},"confidence":0.3},
                  "currency":{"type":"choice","choice":"EUR","probabilities":{"EUR":1.0},"confidence":1.0},
                  "qty_cappuccino":{"type":"choice","choice":"2","probabilities":{"2":1.0},"confidence":1.0},
                  "qty_latte":{"type":"choice","choice":"0","probabilities":{"0":1.0},"confidence":1.0}
                },"usage":{"input_tokens":10,"output_tokens":0}}""";
        try (StubServer s = new StubServer().on("/v1/systemone", 200, answer)) {
            BenchmarkResponse r = kev(s, true, Duration.ofSeconds(5))
                    .execute(new BenchmarkRequest("r", TaskType.EXTRACTION, "Order BE-2026-84921: two cappuccinos, €7.00 total.", "en"));
            assertEquals(Outcome.SUCCESS, r.outcome());
            assertEquals("BE-2026-84921", r.prediction().extraction().orderId());
            assertEquals("€7.00", r.prediction().extraction().total());
            assertEquals(1, r.prediction().extraction().items().size());
            assertEquals("cappuccino", r.prediction().extraction().items().getFirst().product());
            assertTrue(r.timing().stageNanos().containsKey("candidate_extraction"), "candidate stage measured separately");
            JsonNode q = JsonHttp.MAPPER.readTree(s.lastRequest()).get("questions");
            assertEquals("The order ID is \"BE-2026-84921\"", q.path("order_id").path("criteria").path("c1").asText());
        }
    }

    @Test
    void mapsTimeoutAndHttpErrors() {
        try (StubServer s = new StubServer().onSlow("/v1/systemone", "{}", 1500)) {
            assertEquals(Outcome.TIMEOUT, kev(s, false, Duration.ofMillis(200))
                    .execute(new BenchmarkRequest("r", TaskType.CLASSIFICATION, "x", "en")).outcome());
        }
        try (StubServer s = new StubServer().on("/v1/systemone", 500, "{\"error\":\"boom\"}")) {
            BenchmarkResponse r = kev(s, false, Duration.ofSeconds(2)).execute(new BenchmarkRequest("r", TaskType.CLASSIFICATION, "x", "en"));
            assertEquals(Outcome.INFRA_ERROR, r.outcome());
            assertTrue(r.error().contains("boom"));
        }
        try (StubServer s = new StubServer().on("/v1/systemone", 200, "{\"answers\":{}}")) {
            assertEquals(Outcome.MALFORMED, kev(s, false, Duration.ofSeconds(2))
                    .execute(new BenchmarkRequest("r", TaskType.CLASSIFICATION, "x", "en")).outcome());
        }
    }

    @Test
    void healthDetectsOldOllamaAndMissingModel() {
        try (StubServer s = new StubServer().on("/api/version", 200, "{\"version\":\"0.20.2\"}")) {
            assertEquals(HealthStatus.State.RUNTIME_UNSUPPORTED, kev(s, false, Duration.ofSeconds(1)).health().state());
        }
        try (StubServer s = new StubServer().on("/api/version", 200, "{\"version\":\"0.40.15\"}").on("/api/tags", 200, "{\"models\":[]}")) {
            assertEquals(HealthStatus.State.MODEL_MISSING, kev(s, false, Duration.ofSeconds(1)).health().state());
        }
        assertEquals(HealthStatus.State.DOWN, new KevModel("k", "kev", false, new OllamaAdminClient("http://127.0.0.1:1"),
                Duration.ofSeconds(1), "5m").health().state());
    }

    @Test
    void candidateExtractorFindsIdsAndMoney() {
        CandidateExtractor.Candidates c = CandidateExtractor.extract("Order #A7K29Q and ref CB/2026/0042 at table 7, paid € 12,50 or $3.");
        assertEquals(java.util.List.of("A7K29Q", "CB/2026/0042"), c.orderIds());
        assertTrue(c.amounts().contains("€ 12,50"));
        assertTrue(c.amounts().contains("$3"));
    }
}

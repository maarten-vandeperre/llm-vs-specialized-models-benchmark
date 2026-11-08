package dev.beyondllm.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import dev.beyondllm.domain.BenchmarkRequest;
import dev.beyondllm.domain.BenchmarkResponse;
import dev.beyondllm.domain.Outcome;
import dev.beyondllm.domain.TaskType;
import dev.beyondllm.infrastructure.http.JsonHttp;
import dev.beyondllm.infrastructure.nuextract.NuExtractModel;
import dev.beyondllm.infrastructure.ollama.OllamaAdminClient;
import dev.beyondllm.support.StubServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NuExtractModelTest {

    private NuExtractModel model(StubServer s) {
        return new NuExtractModel("nuextract3", "numind/nuextract3:q4_k_m", new OllamaAdminClient(s.url()), Duration.ofSeconds(5), 8192, "5m", false);
    }

    @Test
    void usesTemplateRoleProtocolWithThinkingDisabled() throws Exception {
        try (StubServer s = new StubServer().on("/api/chat", 200, StubServer.resource("nuextract-combined.json"))) {
            BenchmarkResponse r = model(s).execute(new BenchmarkRequest("r", TaskType.COMBINED, "Order BE-2026-84921 ...", "en"));
            assertEquals(Outcome.SUCCESS, r.outcome());
            JsonNode req = JsonHttp.MAPPER.readTree(s.lastRequest());
            assertEquals("template", req.path("messages").get(0).path("role").asText());
            assertEquals("instructions", req.path("messages").get(1).path("role").asText());
            assertEquals("user", req.path("messages").get(2).path("role").asText());
            assertEquals("Order BE-2026-84921 ...", req.path("messages").get(2).path("content").asText());
            assertFalse(req.path("think").asBoolean(true));
            assertFalse(req.path("truncate").asBoolean(true), "inputs are never silently truncated");
            assertEquals(0.0, req.path("options").path("temperature").asDouble(1));
            JsonNode template = JsonHttp.MAPPER.readTree(req.path("messages").get(0).path("content").asText());
            assertEquals("verbatim-string", template.path("order_id").asText());
            assertTrue(template.path("intent").isArray(), "classification via NuExtract enum");

            assertEquals("refund", r.prediction().label("intent"));
            assertNull(r.prediction().labels().get("intent").probability(), "no invented probabilities");
            assertEquals("BE-2026-84921", r.prediction().extraction().orderId());
            assertEquals(2, r.prediction().extraction().items().size());
            assertEquals("12.5", r.prediction().extraction().total());
            assertEquals(72, r.timing().outputTokens());
            assertEquals(1031839999L, r.timing().generationNanos());
            assertEquals(10212906292L, r.timing().loadNanos());
        }
    }

    @Test
    void malformedJsonIsReportedNotHidden() {
        String body = "{\"message\":{\"content\":\"Sure! Here is the data: order BE-1\"},\"eval_count\":5}";
        try (StubServer s = new StubServer().on("/api/chat", 200, body)) {
            BenchmarkResponse r = model(s).execute(new BenchmarkRequest("r", TaskType.EXTRACTION, "x", "en"));
            assertEquals(Outcome.MALFORMED, r.outcome());
            assertEquals(body, r.rawResponse(), "raw response preserved");
        }
    }

    @Test
    void contextOverflowIsUnsupported() {
        try (StubServer s = new StubServer().on("/api/chat", 400, "{\"error\":\"input length exceeds the context length\"}")) {
            assertEquals(Outcome.UNSUPPORTED, model(s).execute(new BenchmarkRequest("r", TaskType.EXTRACTION, "x", "en")).outcome());
        }
    }

    @Test
    void missingClassificationFieldIsMalformed() {
        String body = "{\"message\":{\"content\":\"{\\\"intent\\\":\\\"refund\\\"}\"}}";
        try (StubServer s = new StubServer().on("/api/chat", 200, body)) {
            assertEquals(Outcome.MALFORMED, model(s).execute(new BenchmarkRequest("r", TaskType.CLASSIFICATION, "x", "en")).outcome());
        }
    }
}

package dev.beyondllm.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import dev.beyondllm.domain.BenchmarkRequest;
import dev.beyondllm.domain.BenchmarkResponse;
import dev.beyondllm.domain.HealthStatus;
import dev.beyondllm.domain.Outcome;
import dev.beyondllm.domain.TaskType;
import dev.beyondllm.infrastructure.cloud.CloudLlmModel;
import dev.beyondllm.infrastructure.http.JsonHttp;
import dev.beyondllm.support.StubServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Payload shapes copied from real responses of 2026-11-08 (content shortened). */
class CloudLlmModelTest {

    static final String OPENAI_OK = "{\"id\":\"chatcmpl-1\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":"
            + "\"{\\\"intent\\\":\\\"refund\\\",\\\"sentiment\\\":\\\"negative\\\",\\\"category\\\":\\\"product_quality\\\",\\\"complaint\\\":\\\"temperature\\\","
            + "\\\"order_id\\\":\\\"BE-1\\\",\\\"items\\\":[{\\\"product\\\":\\\"latte\\\",\\\"quantity\\\":2}],\\\"total_amount\\\":9.5,\\\"currency\\\":\\\"EUR\\\"}\","
            + "\"refusal\":null},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":412,\"completion_tokens\":61,"
            + "\"prompt_tokens_details\":{\"cached_tokens\":0}}}";

    static final String ANTHROPIC_OK = "{\"model\":\"claude-haiku-5-5\",\"type\":\"message\",\"content\":[{\"type\":\"text\",\"text\":"
            + "\"{\\\"intent\\\":\\\"refund\\\",\\\"sentiment\\\":\\\"negative\\\",\\\"category\\\":\\\"product_quality\\\",\\\"complaint\\\":\\\"temperature\\\"}\"}],"
            + "\"stop_reason\":\"end_turn\",\"stop_details\":null,\"usage\":{\"input_tokens\":640,\"cache_read_input_tokens\":0,\"output_tokens\":30}}";

    private CloudLlmModel openai(StubServer s, String key) {
        return new CloudLlmModel("openai-x", "x", CloudLlmModel.Provider.OPENAI, "gpt-6-luna", key, s.url(), "none", false, Duration.ofSeconds(5));
    }

    private CloudLlmModel anthropic(StubServer s, boolean disableThinking) {
        return new CloudLlmModel("claude-x", "x", CloudLlmModel.Provider.ANTHROPIC, "claude-haiku-5-5", "k", s.url(), "low", disableThinking, Duration.ofSeconds(5));
    }

    @Test
    void openAiUsesStrictJsonSchemaAndSharedPrompt() throws Exception {
        try (StubServer s = new StubServer().on("/v1/chat/completions", 200, OPENAI_OK)) {
            CloudLlmModel m = openai(s, "k");
            assertTrue(m.requiresExplicitSelection(), "paid API is never selected implicitly");
            BenchmarkResponse r = m.execute(new BenchmarkRequest("r", TaskType.COMBINED, "Two lattes, cold!", "en"));
            assertEquals(Outcome.SUCCESS, r.outcome());
            JsonNode req = JsonHttp.MAPPER.readTree(s.lastRequest());
            assertEquals("gpt-6-luna", req.path("model").asText());
            assertEquals("none", req.path("reasoning_effort").asText());
            assertEquals("system", req.path("messages").get(0).path("role").asText());
            assertTrue(req.path("messages").get(0).path("content").asText().contains("fictional coffee shop"));
            assertEquals("Two lattes, cold!", req.path("messages").get(1).path("content").asText());
            JsonNode js = req.path("response_format").path("json_schema");
            assertTrue(js.path("strict").asBoolean());
            assertFalse(js.path("schema").path("additionalProperties").asBoolean(true));
            assertEquals(8, js.path("schema").path("required").size());
            assertFalse(s.lastRequest().contains("Bearer"), "key is a header, not part of the body");

            assertEquals("refund", r.prediction().label("intent"));
            assertNull(r.prediction().labels().get("intent").probability(), "no invented probabilities");
            assertEquals("BE-1", r.prediction().extraction().orderId());
            assertEquals(412, r.timing().inputTokens());
            assertEquals(61, r.timing().outputTokens());
        }
    }

    @Test
    void anthropicUsesOutputConfigFormatAndOptionalDisabledThinking() throws Exception {
        try (StubServer s = new StubServer().on("/v1/messages", 200, ANTHROPIC_OK)) {
            BenchmarkResponse r = anthropic(s, true).execute(new BenchmarkRequest("r", TaskType.CLASSIFICATION, "cold coffee", "en"));
            assertEquals(Outcome.SUCCESS, r.outcome());
            JsonNode req = JsonHttp.MAPPER.readTree(s.lastRequest());
            assertEquals("disabled", req.path("thinking").path("type").asText());
            assertEquals("low", req.path("output_config").path("effort").asText());
            assertEquals("json_schema", req.path("output_config").path("format").path("type").asText());
            assertEquals(4, req.path("output_config").path("format").path("schema").path("required").size());
            assertTrue(req.path("system").asText().contains("fictional coffee shop"));
            assertFalse(req.has("temperature"));
            assertEquals("temperature", r.prediction().label("complaint"));
            assertEquals(640, r.timing().inputTokens());

            anthropic(s, false).execute(new BenchmarkRequest("r", TaskType.CLASSIFICATION, "x", "en"));
            assertFalse(JsonHttp.MAPPER.readTree(s.lastRequest()).has("thinking"));
        }
    }

    @Test
    void rateLimitIsAnInfraErrorWithProviderMessage() {
        try (StubServer s = new StubServer().on("/v1/chat/completions", 429, "{\"error\":{\"message\":\"Rate limit reached\",\"type\":\"requests\"}}")) {
            BenchmarkResponse r = openai(s, "k").execute(new BenchmarkRequest("r", TaskType.CLASSIFICATION, "x", "en"));
            assertEquals(Outcome.INFRA_ERROR, r.outcome());
            assertTrue(r.error().contains("Rate limit reached"));
        }
    }

    @Test
    void refusalAndTruncationAreNotSuccesses() {
        String refusal = "{\"content\":[],\"stop_reason\":\"refusal\",\"stop_details\":{\"type\":\"refusal\",\"category\":null},\"usage\":{\"input_tokens\":5,\"output_tokens\":0}}";
        try (StubServer s = new StubServer().on("/v1/messages", 200, refusal)) {
            assertEquals(Outcome.ABSTAINED, anthropic(s, true).execute(new BenchmarkRequest("r", TaskType.CLASSIFICATION, "x", "en")).outcome());
        }
        String cut = OPENAI_OK.replace("\"stop\"", "\"length\"");
        try (StubServer s = new StubServer().on("/v1/chat/completions", 200, cut)) {
            assertEquals(Outcome.MALFORMED, openai(s, "k").execute(new BenchmarkRequest("r", TaskType.COMBINED, "x", "en")).outcome());
        }
    }

    @Test
    void missingKeyDisablesTheCandidateWithoutAnyRequest() {
        try (StubServer s = new StubServer()) {
            CloudLlmModel m = openai(s, "");
            assertEquals(HealthStatus.State.DISABLED, m.health().state());
            assertEquals(Outcome.INFRA_ERROR, m.execute(new BenchmarkRequest("r", TaskType.CLASSIFICATION, "x", "en")).outcome());
            assertTrue(s.requests.isEmpty());
        }
    }
}

package dev.beyondllm.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import dev.beyondllm.domain.BenchmarkRequest;
import dev.beyondllm.domain.BenchmarkResponse;
import dev.beyondllm.domain.Outcome;
import dev.beyondllm.domain.TaskType;
import dev.beyondllm.infrastructure.http.JsonHttp;
import dev.beyondllm.infrastructure.ollama.OllamaAdminClient;
import dev.beyondllm.infrastructure.qwen.QwenModel;
import dev.beyondllm.support.StubServer;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QwenModelTest {

    @Test
    void realLangChain4jClientSendsSchemaAndThinkFalseAndKeepsOllamaTimings() throws Exception {
        String content = "{\\\"intent\\\":\\\"refund\\\",\\\"sentiment\\\":\\\"negative\\\",\\\"category\\\":\\\"product_quality\\\",\\\"complaint\\\":\\\"temperature\\\"}";
        String body = "{\"model\":\"qwen3.5:4b\",\"message\":{\"role\":\"assistant\",\"content\":\"" + content + "\"},\"done\":true,"
                + "\"done_reason\":\"stop\",\"total_duration\":5680597375,\"load_duration\":3311656875,\"prompt_eval_count\":35,"
                + "\"eval_count\":31,\"eval_duration\":520865000}";
        try (StubServer s = new StubServer().on("/api/chat", 200, body)) {
            QwenModel q = new QwenModel("qwen", "qwen3.5:4b", new OllamaAdminClient(s.url()), Duration.ofSeconds(5), 8192, false);
            BenchmarkResponse r = q.execute(new BenchmarkRequest("r", TaskType.CLASSIFICATION, "My latte was cold, refund please", "en"));

            assertEquals(Outcome.SUCCESS, r.outcome(), r.error());
            assertEquals("refund", r.prediction().label("intent"));
            assertEquals(5680597375L, r.timing().serverNanos(), "server duration recovered from raw Ollama body");
            assertEquals(31, r.timing().outputTokens());

            JsonNode req = JsonHttp.MAPPER.readTree(s.lastRequest());
            assertFalse(req.path("think").asBoolean(true), "thinking disabled");
            assertTrue(req.path("format").isObject(), "native JSON-Schema structured output");
            assertTrue(req.path("format").path("properties").path("intent").has("enum"));
            assertEquals(0.0, req.path("options").path("temperature").asDouble(1));
            assertEquals(42, req.path("options").path("seed").asInt());
        }
    }

    @Test
    void mockedChatModelOutputIsMappedAndMalformedDetected() {
        ChatModel chat = mock(ChatModel.class);
        when(chat.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("{\"order_id\":\"BE-1\",\"items\":[{\"product\":\"latte\",\"quantity\":2}],\"total_amount\":7.6,\"currency\":\"EUR\"}"))
                .build());
        QwenModel q = new QwenModel("qwen", "qwen3.5:4b", new OllamaAdminClient("http://127.0.0.1:1"), chat, 8192, false);
        BenchmarkResponse ok = q.execute(new BenchmarkRequest("r", TaskType.EXTRACTION, "x", "en"));
        assertEquals("BE-1", ok.prediction().extraction().orderId());
        assertEquals("2", ok.prediction().extraction().items().getFirst().quantity());

        when(chat.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("I think it is a refund")).build());
        assertEquals(Outcome.MALFORMED, q.execute(new BenchmarkRequest("r", TaskType.CLASSIFICATION, "x", "en")).outcome());

        when(chat.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("connection refused"));
        assertEquals(Outcome.INFRA_ERROR, q.execute(new BenchmarkRequest("r", TaskType.CLASSIFICATION, "x", "en")).outcome());
    }

    @Test
    void schemaHasSameFieldsAsNuExtractTemplate() {
        var schema = QwenModel.schema(TaskType.COMBINED);
        var props = ((dev.langchain4j.model.chat.request.json.JsonObjectSchema) schema.rootElement()).properties().keySet();
        assertEquals(dev.beyondllm.infrastructure.nuextract.NuExtractModel.template(TaskType.COMBINED).keySet(), props);
    }
}

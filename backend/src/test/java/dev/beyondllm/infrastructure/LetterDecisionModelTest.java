package dev.beyondllm.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import dev.beyondllm.domain.BenchmarkRequest;
import dev.beyondllm.domain.BenchmarkResponse;
import dev.beyondllm.domain.LabelPrediction;
import dev.beyondllm.domain.Outcome;
import dev.beyondllm.domain.TaskType;
import dev.beyondllm.infrastructure.http.JsonHttp;
import dev.beyondllm.infrastructure.letterdecision.LetterDecisionModel;
import dev.beyondllm.infrastructure.ollama.OllamaAdminClient;
import dev.beyondllm.support.StubServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LetterDecisionModelTest {

    // A first, B second, a non-letter token and a lowercase letter that must be ignored.
    static final String ANSWER = """
            {"model":"gemma4:12b","message":{"role":"assistant","content":"A"},"done":true,
             "total_duration":200000000,"load_duration":1000,"prompt_eval_count":150,"eval_count":1,
             "logprobs":[{"token":"A","logprob":-0.2231,"top_logprobs":[
               {"token":"A","logprob":-0.2231},{"token":"<|channel>","logprob":-1.0},
               {"token":"B","logprob":-1.6094},{"token":"a","logprob":-2.0}]}]}""";

    private LetterDecisionModel model(StubServer s) {
        return new LetterDecisionModel("gemma4-12b-decision", "Gemma 4 12B", "gemma4:12b", new OllamaAdminClient(s.url()),
                Duration.ofSeconds(5), "5m", 16384);
    }

    @Test
    void oneCallPerFieldWithLetterSoftmax() throws Exception {
        try (StubServer s = new StubServer().on("/api/chat", 200, ANSWER)) {
            BenchmarkResponse r = model(s).execute(new BenchmarkRequest("r1", TaskType.CLASSIFICATION, "My latte was cold, refund please.", "en"));

            assertEquals(Outcome.SUCCESS, r.outcome());
            assertEquals(4, s.requests.size(), "one call per label field");
            LabelPrediction intent = r.prediction().labels().get("intent");
            assertEquals("refund", intent.label(), "A = first option");
            // softmax over A and B only: 0.8 / (0.8 + 0.2)
            assertEquals(0.8, intent.probability(), 1e-3);
            assertEquals(0.2, intent.distribution().get("replacement"), 1e-3);
            assertEquals(0.0, intent.distribution().get("none"), 1e-12, "letters outside the top log-probabilities get 0");
            assertEquals(600, r.timing().inputTokens(), "summed over the four calls");

            JsonNode req = JsonHttp.MAPPER.readTree(s.lastRequest());
            assertEquals(1, req.path("options").path("num_predict").asInt());
            assertEquals(false, req.path("think").asBoolean(true));
            String prompt = req.path("messages").path(1).path("content").asText();
            assertTrue(prompt.startsWith("Customer message:"), "message first so the four calls share a prefix");
            assertTrue(prompt.contains("A) "), prompt);
        }
    }

    @Test
    void noLetterIsMalformed() {
        String bad = "{\"message\":{\"content\":\"I\"},\"logprobs\":[{\"token\":\"I\",\"top_logprobs\":[{\"token\":\"I\",\"logprob\":0}]}]}";
        try (StubServer s = new StubServer().on("/api/chat", 200, bad)) {
            BenchmarkResponse r = model(s).execute(new BenchmarkRequest("r", TaskType.CLASSIFICATION, "Hi", "en"));
            assertEquals(Outcome.MALFORMED, r.outcome());
        }
    }

    @Test
    void extractionIsUnsupported() {
        try (StubServer s = new StubServer()) {
            BenchmarkResponse r = model(s).execute(new BenchmarkRequest("r", TaskType.EXTRACTION, "Order X", "en"));
            assertEquals(Outcome.UNSUPPORTED, r.outcome());
            assertTrue(s.requests.isEmpty());
        }
    }
}

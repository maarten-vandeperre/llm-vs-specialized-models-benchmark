package dev.beyondllm.infrastructure.nuextract;

import com.fasterxml.jackson.databind.JsonNode;
import dev.beyondllm.application.BenchmarkModel;
import dev.beyondllm.application.ModelInfo;
import dev.beyondllm.domain.BenchmarkRequest;
import dev.beyondllm.domain.BenchmarkResponse;
import dev.beyondllm.domain.Capability;
import dev.beyondllm.domain.HealthStatus;
import dev.beyondllm.domain.LabelSchema;
import dev.beyondllm.domain.ModelCapabilities;
import dev.beyondllm.domain.Outcome;
import dev.beyondllm.domain.Prediction;
import dev.beyondllm.domain.TaskType;
import dev.beyondllm.domain.Timing;
import dev.beyondllm.infrastructure.GenerativeOutputMapper;
import dev.beyondllm.infrastructure.PromptLoader;
import dev.beyondllm.infrastructure.http.JsonHttp;
import dev.beyondllm.infrastructure.ollama.OllamaAdminClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NuExtract3 via Ollama {@code /api/chat}, using the model's own template protocol (verified from the
 * {@code numind/nuextract3:q4_k_m} Ollama chat template): the JSON template is sent as a message with role
 * {@code template}, optional guidance with role {@code instructions}, and the document as the {@code user} message.
 * Thinking is disabled ({@code think:false}; the template then emits an empty think block). Classification is done
 * with NuExtract enum fields (a JSON array of allowed values), which is the documented way to constrain values.
 */
public class NuExtractModel implements BenchmarkModel {

    private final String id;
    private final String tag;
    private final OllamaAdminClient admin;
    private final JsonHttp http;
    private final Duration timeout;
    private final int numCtx;
    private final String keepAlive;
    private final boolean think;

    public NuExtractModel(String id, String tag, OllamaAdminClient admin, Duration timeout, int numCtx, String keepAlive, boolean think) {
        this.id = id;
        this.tag = tag;
        this.admin = admin;
        this.http = new JsonHttp(Duration.ofSeconds(5));
        this.timeout = timeout;
        this.numCtx = numCtx;
        this.keepAlive = keepAlive;
        this.think = think;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public ModelInfo info() {
        return new ModelInfo(id, "NuExtract3 (Q4_K_M)", "model", "ollama /api/chat (template role)", tag,
                "generative extraction LLM", "Q4_K_M", "ollama",
                "think=" + think + ", temperature=0, seed=42, num_ctx=" + numCtx + ", truncate=false");
    }

    @Override
    public ModelCapabilities capabilities() {
        return new ModelCapabilities(EnumSet.of(Capability.CLASSIFICATION, Capability.ARBITRARY_TEXT_EXTRACTION,
                Capability.STRUCTURED_EXTRACTION, Capability.MULTILINGUAL, Capability.COMBINED_SINGLE_CALL),
                EnumSet.allOf(TaskType.class), numCtx);
    }

    @Override
    public HealthStatus health() {
        return admin.health(id, tag, null);
    }

    @Override
    public List<String> runtimeModels() {
        return List.of(tag);
    }

    @Override
    public boolean unload() {
        return admin.unload(tag);
    }

    /** NuExtract template for a task; field names match {@link GenerativeOutputMapper}. */
    public static Map<String, Object> template(TaskType task) {
        Map<String, Object> t = new LinkedHashMap<>();
        if (task.needsClassification()) {
            for (String field : LabelSchema.FIELDS) {
                t.put(field, new ArrayList<>(LabelSchema.labels(field)));
            }
        }
        if (task.needsExtraction()) {
            t.put("order_id", "verbatim-string");
            t.put("items", List.of(Map.of("product", "string", "quantity", "integer")));
            t.put("total_amount", "number");
            t.put("currency", List.of("EUR", "USD", "GBP"));
        }
        return t;
    }

    @Override
    public BenchmarkResponse execute(BenchmarkRequest req) {
        long t0 = System.nanoTime();
        String templateJson;
        try {
            templateJson = JsonHttp.MAPPER.writeValueAsString(template(req.task()));
        } catch (Exception e) {
            return BenchmarkResponse.failure(id, req, Outcome.INFRA_ERROR, e.getMessage(), null, null);
        }
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "template", "content", templateJson));
        messages.add(Map.of("role", "instructions", "content", PromptLoader.load("nuextract-instructions-v1.txt")));
        messages.add(Map.of("role", "user", "content", req.text()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", tag);
        body.put("messages", messages);
        body.put("stream", false);
        body.put("think", think);
        body.put("keep_alive", keepAlive);
        body.put("truncate", false);
        body.put("options", Map.of("temperature", 0.0, "seed", 42, "num_ctx", numCtx));

        JsonHttp.Result r = http.postJson(admin.baseUrl() + "/api/chat", body, timeout);
        long client = System.nanoTime() - t0;
        if (r.failure() == JsonHttp.Failure.TIMEOUT) {
            return BenchmarkResponse.failure(id, req, Outcome.TIMEOUT, r.error(), Timing.client(client), null);
        }
        if (!r.ok()) {
            String msg = r.errorMessage();
            Outcome o = r.status() == 400 && msg.toLowerCase().contains("context") ? Outcome.UNSUPPORTED : Outcome.INFRA_ERROR;
            return BenchmarkResponse.failure(id, req, o, msg, Timing.client(client), r.body());
        }
        JsonNode root;
        try {
            root = r.json();
        } catch (Exception e) {
            return BenchmarkResponse.failure(id, req, Outcome.MALFORMED, "Non-JSON HTTP body", Timing.client(client), r.body());
        }
        Timing timing = OllamaTimings.from(root, client);
        String content = root.path("message").path("content").asText(null);
        try {
            Prediction p = GenerativeOutputMapper.map(content, req.task());
            return BenchmarkResponse.success(id, req, p, timing, r.body(), Map.of("doneReason", root.path("done_reason").asText("")));
        } catch (GenerativeOutputMapper.MalformedOutputException e) {
            return BenchmarkResponse.failure(id, req, Outcome.MALFORMED, e.getMessage(), timing, r.body());
        }
    }

    /** Parses Ollama's nanosecond timing fields. */
    public static final class OllamaTimings {
        private OllamaTimings() {
        }

        public static Timing from(JsonNode root, long clientNanos) {
            return new Timing(clientNanos,
                    root.has("total_duration") ? root.get("total_duration").asLong() : null,
                    root.has("load_duration") ? root.get("load_duration").asLong() : null,
                    root.has("prompt_eval_count") ? root.get("prompt_eval_count").asInt() : null,
                    root.has("eval_count") ? root.get("eval_count").asInt() : null,
                    root.has("eval_duration") ? root.get("eval_duration").asLong() : null,
                    Map.of());
        }
    }
}

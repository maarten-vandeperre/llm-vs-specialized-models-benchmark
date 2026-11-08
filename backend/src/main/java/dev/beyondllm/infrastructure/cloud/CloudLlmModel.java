package dev.beyondllm.infrastructure.cloud;

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

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Commercial hosted LLM (OpenAI Chat Completions or Anthropic Messages) used as an optional comparison point.
 *
 * <p>Same task contract as the local Qwen baseline: the identical system prompt ({@code qwen-system-v1.txt}) and the
 * identical output fields, enforced with the provider's native strict JSON-Schema structured output. Each model runs
 * at the lowest reasoning setting it accepts (recorded in {@link #info()}). Raw HTTP through the same JDK client as the
 * other adapters, so client-side latency is measured the same way for both providers. The API key is only sent as a
 * request header; it is never logged or written to results. No retries: a rate-limit or server error is recorded as
 * {@link Outcome#INFRA_ERROR}, never hidden.
 */
public class CloudLlmModel implements BenchmarkModel {

    public enum Provider { OPENAI, ANTHROPIC }

    static final String ANTHROPIC_VERSION = "2023-06-01";
    static final int MAX_OUTPUT_TOKENS = 4096;
    static final String PROMPT = "qwen-system-v1.txt";

    private final String id;
    private final String displayName;
    private final Provider provider;
    private final String model;
    private final String apiKey;
    private final String baseUrl;
    private final String reasoning;
    private final boolean disableThinking;
    private final Duration timeout;
    private final JsonHttp http;

    /**
     * @param reasoning       OpenAI {@code reasoning_effort} or Anthropic {@code output_config.effort}
     * @param disableThinking Anthropic only: send {@code thinking: {type: disabled}} (not accepted by every model)
     */
    public CloudLlmModel(String id, String displayName, Provider provider, String model, String apiKey, String baseUrl,
                         String reasoning, boolean disableThinking, Duration timeout) {
        this.id = id;
        this.displayName = displayName;
        this.provider = provider;
        this.model = model;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.reasoning = reasoning;
        this.disableThinking = disableThinking;
        this.timeout = timeout;
        this.http = new JsonHttp(Duration.ofSeconds(10));
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean requiresExplicitSelection() {
        return true;
    }

    private String settings() {
        return provider == Provider.OPENAI
                ? "reasoning_effort=" + reasoning
                : "effort=" + reasoning + ", thinking=" + (disableThinking ? "disabled" : "adaptive (cannot be disabled)");
    }

    @Override
    public ModelInfo info() {
        String runtime = provider == Provider.OPENAI ? "OpenAI Chat Completions API (commercial, hosted)"
                : "Anthropic Messages API (commercial, hosted)";
        return new ModelInfo(id, displayName, "model", runtime, model, "general-purpose LLM (commercial API)",
                null, null, settings() + ", strict JSON-Schema structured output, max output tokens " + MAX_OUTPUT_TOKENS
                + ", no retries, prompt " + PROMPT + "#" + PromptLoader.sha256(PROMPT));
    }

    @Override
    public ModelCapabilities capabilities() {
        return new ModelCapabilities(EnumSet.of(Capability.CLASSIFICATION, Capability.ARBITRARY_TEXT_EXTRACTION,
                Capability.STRUCTURED_EXTRACTION, Capability.MULTILINGUAL, Capability.COMBINED_SINGLE_CALL),
                EnumSet.allOf(TaskType.class), null);
    }

    private Map<String, String> headers() {
        return provider == Provider.OPENAI
                ? Map.of("Authorization", "Bearer " + apiKey)
                : Map.of("x-api-key", apiKey, "anthropic-version", ANTHROPIC_VERSION);
    }

    @Override
    public HealthStatus health() {
        String envVar = provider == Provider.OPENAI ? "OPENAI_API_KEY" : "ANTHROPIC_API_KEY";
        if (apiKey.isEmpty()) {
            return new HealthStatus(id, HealthStatus.State.DISABLED, envVar + " not set (commercial API, opt-in)", Map.of());
        }
        JsonHttp.Result r = http.get(baseUrl + "/v1/models/" + model, Duration.ofSeconds(15), headers());
        if (r.failure() != JsonHttp.Failure.NONE) {
            return new HealthStatus(id, HealthStatus.State.DOWN, r.errorMessage(), Map.of());
        }
        if (r.status() == 404) {
            return new HealthStatus(id, HealthStatus.State.MODEL_MISSING, r.errorMessage(), Map.of());
        }
        if (!r.ok()) {
            return new HealthStatus(id, HealthStatus.State.DOWN, r.errorMessage(), Map.of());
        }
        return HealthStatus.up(id, provider.name().toLowerCase() + " API reachable", Map.of("model", model));
    }

    /** Strict JSON Schema with the same fields as {@code QwenModel.schema}; both providers require additionalProperties=false. */
    public static Map<String, Object> schema(TaskType task) {
        Map<String, Object> props = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        if (task.needsClassification()) {
            for (String field : LabelSchema.FIELDS) {
                props.put(field, Map.of("type", "string", "enum", LabelSchema.labels(field)));
                required.add(field);
            }
        }
        if (task.needsExtraction()) {
            props.put("order_id", nullable(Map.of("type", "string")));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "object");
            item.put("properties", Map.of("product", Map.of("type", "string"), "quantity", Map.of("type", "integer")));
            item.put("required", List.of("product", "quantity"));
            item.put("additionalProperties", false);
            props.put("items", Map.of("type", "array", "items", item));
            props.put("total_amount", nullable(Map.of("type", "number")));
            props.put("currency", nullable(Map.of("type", "string", "enum", List.of("EUR", "USD", "GBP"))));
            required.addAll(List.of("order_id", "items", "total_amount", "currency"));
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "object");
        root.put("properties", props);
        root.put("required", required);
        root.put("additionalProperties", false);
        return root;
    }

    private static Map<String, Object> nullable(Map<String, Object> s) {
        return Map.of("anyOf", List.of(s, Map.of("type", "null")));
    }

    Map<String, Object> requestBody(BenchmarkRequest req) {
        String system = PromptLoader.load(PROMPT);
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("model", model);
        if (provider == Provider.OPENAI) {
            b.put("messages", List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", req.text())));
            b.put("reasoning_effort", reasoning);
            b.put("max_completion_tokens", MAX_OUTPUT_TOKENS);
            b.put("response_format", Map.of("type", "json_schema", "json_schema",
                    Map.of("name", "coffee_" + req.task().name().toLowerCase(), "strict", true, "schema", schema(req.task()))));
        } else {
            b.put("max_tokens", MAX_OUTPUT_TOKENS);
            b.put("system", system);
            b.put("messages", List.of(Map.of("role", "user", "content", req.text())));
            if (disableThinking) {
                b.put("thinking", Map.of("type", "disabled"));
            }
            b.put("output_config", Map.of("effort", reasoning, "format", Map.of("type", "json_schema", "schema", schema(req.task()))));
        }
        return b;
    }

    @Override
    public BenchmarkResponse execute(BenchmarkRequest req) {
        if (apiKey.isEmpty()) {
            return BenchmarkResponse.failure(id, req, Outcome.INFRA_ERROR, "API key not configured", Timing.client(0), null);
        }
        String path = provider == Provider.OPENAI ? "/v1/chat/completions" : "/v1/messages";
        JsonHttp.Result r = http.postJson(baseUrl + path, requestBody(req), timeout, headers());
        Timing client = Timing.client(r.nanos());
        if (r.failure() == JsonHttp.Failure.TIMEOUT) {
            return BenchmarkResponse.failure(id, req, Outcome.TIMEOUT, r.errorMessage(), client, null);
        }
        if (!r.ok()) {
            String msg = r.errorMessage();
            Outcome o = r.status() == 400 && msg.toLowerCase().contains("context") ? Outcome.UNSUPPORTED : Outcome.INFRA_ERROR;
            return BenchmarkResponse.failure(id, req, o, msg, client, r.body());
        }
        JsonNode root;
        try {
            root = r.json();
        } catch (Exception e) {
            return BenchmarkResponse.failure(id, req, Outcome.MALFORMED, "Response is not JSON", client, r.body());
        }
        String content;
        Timing timing;
        if (provider == Provider.OPENAI) {
            JsonNode msg = root.path("choices").path(0).path("message");
            JsonNode usage = root.path("usage");
            timing = tokens(r.nanos(), usage.path("prompt_tokens"), usage.path("completion_tokens"));
            if (msg.hasNonNull("refusal")) {
                return BenchmarkResponse.failure(id, req, Outcome.ABSTAINED, "refusal: " + msg.get("refusal").asText(), timing, r.body());
            }
            if ("length".equals(root.path("choices").path(0).path("finish_reason").asText())) {
                return BenchmarkResponse.failure(id, req, Outcome.MALFORMED, "output hit max_completion_tokens", timing, r.body());
            }
            content = msg.path("content").asText(null);
        } else {
            JsonNode usage = root.path("usage");
            timing = tokens(r.nanos(), usage.path("input_tokens"), usage.path("output_tokens"));
            String stop = root.path("stop_reason").asText();
            if ("refusal".equals(stop)) {
                return BenchmarkResponse.failure(id, req, Outcome.ABSTAINED, "refusal: " + root.path("stop_details").toString(), timing, r.body());
            }
            if ("max_tokens".equals(stop)) {
                return BenchmarkResponse.failure(id, req, Outcome.MALFORMED, "output hit max_tokens", timing, r.body());
            }
            StringBuilder sb = new StringBuilder();
            for (JsonNode block : root.path("content")) {
                if ("text".equals(block.path("type").asText())) {
                    sb.append(block.path("text").asText());
                }
            }
            content = sb.toString();
        }
        try {
            Prediction p = GenerativeOutputMapper.map(content, req.task());
            return BenchmarkResponse.success(id, req, p, timing, r.body(), Map.of());
        } catch (GenerativeOutputMapper.MalformedOutputException e) {
            return BenchmarkResponse.failure(id, req, Outcome.MALFORMED, e.getMessage(), timing, r.body());
        }
    }

    private static Timing tokens(long nanos, JsonNode in, JsonNode out) {
        return new Timing(nanos, null, null, in.isNumber() ? in.asInt() : null, out.isNumber() ? out.asInt() : null, null, Map.of());
    }
}

package dev.beyondllm.infrastructure.qwen;

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
import dev.beyondllm.infrastructure.nuextract.NuExtractModel;
import dev.beyondllm.infrastructure.ollama.OllamaAdminClient;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNullSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.OllamaChatModel;

import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * General-purpose LLM baseline (Qwen3.5-4B) through LangChain4j's {@link ChatModel} abstraction (version managed by
 * the Quarkus LangChain4j BOM). Uses native structured output: the JSON Schema is passed as Ollama's {@code format},
 * so decoding is grammar-constrained rather than post-processed. Thinking is explicitly disabled
 * ({@code think=false}), temperature 0 and a fixed seed are used, and input truncation is disabled.
 */
public class QwenModel implements BenchmarkModel {

    private final String id;
    private final String tag;
    private final OllamaAdminClient admin;
    private final ChatModel chatModel;
    private final int numCtx;
    private final boolean think;

    public QwenModel(String id, String tag, OllamaAdminClient admin, Duration timeout, int numCtx, boolean think) {
        this(id, tag, admin, OllamaChatModel.builder()
                .httpClientBuilder(new RecordingHttpClient.Builder())
                .baseUrl(admin.baseUrl())
                .modelName(tag)
                .temperature(0.0)
                .seed(42)
                .numCtx(numCtx)
                .think(think)
                .returnThinking(false)
                .truncate(false)
                .timeout(timeout)
                .build(), numCtx, think);
    }

    /** Visible for tests: inject any LangChain4j ChatModel. */
    public QwenModel(String id, String tag, OllamaAdminClient admin, ChatModel chatModel, int numCtx, boolean think) {
        this.id = id;
        this.tag = tag;
        this.admin = admin;
        this.chatModel = chatModel;
        this.numCtx = numCtx;
        this.think = think;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public ModelInfo info() {
        return new ModelInfo(id, "Qwen3.5 4B", "model", "ollama /api/chat via LangChain4j ChatModel", tag,
                "general-purpose LLM", "Q4_K_M", "ollama",
                "think=" + think + ", temperature=0, seed=42, num_ctx=" + numCtx + ", JSON-Schema constrained output, prompt "
                        + "qwen-system-v1.txt#" + PromptLoader.sha256("qwen-system-v1.txt"));
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

    static JsonSchemaElement nullable(JsonSchemaElement e) {
        return JsonAnyOfSchema.builder().anyOf(e, new JsonNullSchema()).build();
    }

    /** JSON Schema equivalent of the NuExtract template – identical fields for both generative models. */
    public static JsonSchema schema(TaskType task) {
        JsonObjectSchema.Builder root = JsonObjectSchema.builder();
        List<String> required = new ArrayList<>();
        if (task.needsClassification()) {
            for (String field : LabelSchema.FIELDS) {
                root.addProperty(field, JsonEnumSchema.builder().enumValues(LabelSchema.labels(field)).build());
                required.add(field);
            }
        }
        if (task.needsExtraction()) {
            root.addProperty("order_id", nullable(new JsonStringSchema()));
            root.addProperty("items", JsonArraySchema.builder().items(JsonObjectSchema.builder()
                    .addProperty("product", new JsonStringSchema())
                    .addProperty("quantity", new JsonIntegerSchema())
                    .required("product", "quantity").build()).build());
            root.addProperty("total_amount", nullable(new JsonNumberSchema()));
            root.addProperty("currency", nullable(JsonEnumSchema.builder().enumValues("EUR", "USD", "GBP").build()));
            required.addAll(List.of("order_id", "items", "total_amount", "currency"));
        }
        root.required(required);
        return JsonSchema.builder().name("coffee_" + task.name().toLowerCase()).rootElement(root.build()).build();
    }

    @Override
    public BenchmarkResponse execute(BenchmarkRequest req) {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(SystemMessage.from(PromptLoader.load("qwen-system-v1.txt")), UserMessage.from(req.text()))
                .responseFormat(ResponseFormat.builder().type(ResponseFormatType.JSON).jsonSchema(schema(req.task())).build())
                .build();
        long t0 = System.nanoTime();
        ChatResponse resp;
        RecordingHttpClient.takeLastBody();
        try {
            resp = chatModel.chat(chatRequest);
        } catch (RuntimeException e) {
            long client = System.nanoTime() - t0;
            String raw = RecordingHttpClient.takeLastBody();
            if (hasCause(e, HttpTimeoutException.class) || hasCause(e, java.util.concurrent.TimeoutException.class)) {
                return BenchmarkResponse.failure(id, req, Outcome.TIMEOUT, e.getMessage(), Timing.client(client), raw);
            }
            HttpException he = findCause(e, HttpException.class);
            if (he != null && he.statusCode() == 400 && String.valueOf(he.getMessage()).toLowerCase().contains("context")) {
                return BenchmarkResponse.failure(id, req, Outcome.UNSUPPORTED, he.getMessage(), Timing.client(client), raw);
            }
            return BenchmarkResponse.failure(id, req, Outcome.INFRA_ERROR, e.getClass().getSimpleName() + ": " + e.getMessage(),
                    Timing.client(client), raw);
        }
        long client = System.nanoTime() - t0;
        String raw = RecordingHttpClient.takeLastBody();
        Timing timing = Timing.client(client);
        if (raw != null) {
            try {
                timing = NuExtractModel.OllamaTimings.from(JsonHttp.MAPPER.readTree(raw), client);
            } catch (Exception ignored) {
                // keep client timing
            }
        }
        if (timing.outputTokens() == null && resp.tokenUsage() != null) {
            timing = new Timing(client, null, null, resp.tokenUsage().inputTokenCount(), resp.tokenUsage().outputTokenCount(), null, Map.of());
        }
        try {
            Prediction p = GenerativeOutputMapper.map(resp.aiMessage().text(), req.task());
            return BenchmarkResponse.success(id, req, p, timing, raw != null ? raw : resp.aiMessage().text(), Map.of());
        } catch (GenerativeOutputMapper.MalformedOutputException e) {
            return BenchmarkResponse.failure(id, req, Outcome.MALFORMED, e.getMessage(), timing, raw);
        }
    }

    private static boolean hasCause(Throwable t, Class<? extends Throwable> type) {
        return findCause(t, type) != null;
    }

    private static <T extends Throwable> T findCause(Throwable t, Class<T> type) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (type.isInstance(c)) {
                return type.cast(c);
            }
        }
        return null;
    }
}

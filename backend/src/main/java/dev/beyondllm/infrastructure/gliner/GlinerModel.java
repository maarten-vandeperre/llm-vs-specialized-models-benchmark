package dev.beyondllm.infrastructure.gliner;

import com.fasterxml.jackson.databind.JsonNode;
import dev.beyondllm.application.BenchmarkModel;
import dev.beyondllm.application.ModelInfo;
import dev.beyondllm.domain.BenchmarkRequest;
import dev.beyondllm.domain.BenchmarkResponse;
import dev.beyondllm.domain.Capability;
import dev.beyondllm.domain.Extraction;
import dev.beyondllm.domain.HealthStatus;
import dev.beyondllm.domain.LabelPrediction;
import dev.beyondllm.domain.LabelSchema;
import dev.beyondllm.domain.ModelCapabilities;
import dev.beyondllm.domain.Outcome;
import dev.beyondllm.domain.Prediction;
import dev.beyondllm.domain.Span;
import dev.beyondllm.domain.TaskType;
import dev.beyondllm.domain.Timing;
import dev.beyondllm.infrastructure.http.JsonHttp;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GLiNER2 family (GLiNER2.5 base/small/multi and GLiNER2.5-Decide) through the project's Python FastAPI service
 * wrapping the official {@code gliner2} library. Non-generative: entities are spans of the input with offsets and
 * confidences; classification returns the chosen label with a confidence (no full distribution is exposed by the library).
 * <p>
 * Line items: GLiNER returns independent product and quantity spans; this adapter pairs a quantity with the product
 * immediately following (or preceding) it within {@value #PAIR_GAP} characters. This deterministic post-processing
 * is part of the GLiNER candidate and included in its latency.
 */
public class GlinerModel implements BenchmarkModel {

    static final int PAIR_GAP = 4;

    public static final Map<String, String> ENTITY_LABELS = new LinkedHashMap<>();

    static {
        ENTITY_LABELS.put("order_id", "order number or order reference code");
        ENTITY_LABELS.put("product", "coffee shop product that was ordered, such as a drink or pastry");
        ENTITY_LABELS.put("quantity", "number of items ordered");
        ENTITY_LABELS.put("total_amount", "total price paid for the order including the currency");
    }

    private final String id;
    private final String checkpoint;
    private final String displayName;
    private final Set<TaskType> tasks;
    private final boolean multilingual;
    private final String baseUrl;
    private final JsonHttp http;
    private final Duration timeout;
    private final double threshold;

    public GlinerModel(String id, String checkpoint, String displayName, Set<TaskType> tasks, boolean multilingual,
                       String baseUrl, Duration timeout, double threshold) {
        this.id = id;
        this.checkpoint = checkpoint;
        this.displayName = displayName;
        this.tasks = tasks;
        this.multilingual = multilingual;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.http = new JsonHttp(Duration.ofSeconds(3));
        this.timeout = timeout;
        this.threshold = threshold;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public ModelInfo info() {
        return new ModelInfo(id, displayName, "model", "gliner2 python service (FastAPI)", checkpoint,
                tasks.contains(TaskType.EXTRACTION) ? "schema-driven span extractor + classifier (encoder)" : "encoder decision/classification model",
                null, processPattern(baseUrl), "service " + baseUrl + "; threshold=" + threshold + "; label confidence is a model score, not a calibrated probability");
    }

    @Override
    public ModelCapabilities capabilities() {
        EnumSet<Capability> f = EnumSet.of(Capability.CLASSIFICATION, Capability.LABEL_CONFIDENCE);
        if (tasks.contains(TaskType.EXTRACTION)) {
            f.addAll(EnumSet.of(Capability.ARBITRARY_TEXT_EXTRACTION, Capability.STRUCTURED_EXTRACTION,
                    Capability.ENTITY_CONFIDENCE, Capability.OFFSETS, Capability.COMBINED_SINGLE_CALL));
        }
        if (multilingual) {
            f.add(Capability.MULTILINGUAL);
        }
        return new ModelCapabilities(f, tasks, 4096);
    }

    @Override
    public HealthStatus health() {
        JsonHttp.Result r = http.get(baseUrl + "/health", Duration.ofSeconds(5));
        if (!r.ok()) {
            return HealthStatus.of(id, HealthStatus.State.DOWN, "GLiNER service not reachable at " + baseUrl + " (" + r.errorMessage() + ")");
        }
        try {
            JsonNode m = r.json().path("models").path(checkpoint);
            if (m.isMissingNode()) {
                return HealthStatus.of(id, HealthStatus.State.MODEL_MISSING, "Checkpoint " + checkpoint
                        + " not loaded by the service. Add it to GLINER_MODELS and restart the service.");
            }
            Map<String, Object> info = JsonHttp.MAPPER.convertValue(m, Map.class);
            info.put("versions", JsonHttp.MAPPER.convertValue(r.json().path("versions"), Map.class));
            return HealthStatus.up(id, "loaded on " + m.path("device").asText("?"), info);
        } catch (Exception e) {
            return HealthStatus.of(id, HealthStatus.State.DOWN, "Bad /health payload: " + e.getMessage());
        }
    }

    @Override
    public BenchmarkResponse execute(BenchmarkRequest req) {
        if (!tasks.contains(req.task())) {
            return BenchmarkResponse.unsupported(id, req, displayName + " is configured for " + tasks + " only");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", checkpoint);
        body.put("text", req.text());
        body.put("threshold", threshold);
        if (req.task().needsExtraction()) {
            body.put("entities", ENTITY_LABELS);
        }
        if (req.task().needsClassification()) {
            Map<String, Object> cls = new LinkedHashMap<>();
            for (String field : LabelSchema.FIELDS) {
                cls.put(field, LabelSchema.descriptions(field));
            }
            body.put("classifications", cls);
        }
        long t0 = System.nanoTime();
        JsonHttp.Result r = http.postJson(baseUrl + "/v1/extract", body, timeout);
        long client = System.nanoTime() - t0;
        if (r.failure() == JsonHttp.Failure.TIMEOUT) {
            return BenchmarkResponse.failure(id, req, Outcome.TIMEOUT, r.error(), Timing.client(client), null);
        }
        if (r.status() == 413) {
            return BenchmarkResponse.failure(id, req, Outcome.UNSUPPORTED, r.errorMessage(), Timing.client(client), r.body());
        }
        if (!r.ok()) {
            return BenchmarkResponse.failure(id, req, Outcome.INFRA_ERROR, r.errorMessage(), Timing.client(client), r.body());
        }
        try {
            long p0 = System.nanoTime();
            JsonNode root = r.json();
            JsonNode result = root.path("result");
            Map<String, LabelPrediction> labels = new LinkedHashMap<>();
            if (req.task().needsClassification()) {
                for (String field : LabelSchema.FIELDS) {
                    labels.put(field, parseLabel(result.path(field)));
                }
            }
            Extraction ex = req.task().needsExtraction() ? parseEntities(result.path("entities")) : null;
            long post = System.nanoTime() - p0;
            long serverNanos = (long) (root.path("inference_ms").asDouble() * 1_000_000);
            Timing timing = new Timing(System.nanoTime() - t0, serverNanos, null,
                    root.has("input_tokens") ? root.get("input_tokens").asInt() : null, null, null,
                    Map.of("gliner_http", client, "postprocess", post));
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("chunked", root.path("chunked").asBoolean(false));
            meta.put("device", root.path("device").asText(""));
            return BenchmarkResponse.success(id, req, Prediction.of(labels, ex), timing, r.body(), meta);
        } catch (Exception e) {
            return BenchmarkResponse.failure(id, req, Outcome.MALFORMED, "Unparseable GLiNER response: " + e.getMessage(),
                    Timing.client(client), r.body());
        }
    }

    /** Each GLiNER checkpoint can run in its own service process (own port) so RSS/CPU are attributable per model. */
    static String processPattern(String baseUrl) {
        try {
            int port = java.net.URI.create(baseUrl).getPort();
            return port > 0 ? "--port " + port : "uvicorn";
        } catch (Exception e) {
            return "uvicorn";
        }
    }

    static LabelPrediction parseLabel(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            throw new IllegalStateException("missing classification result");
        }
        if (n.isTextual()) {
            return LabelPrediction.of(n.asText());
        }
        if (n.isArray() && !n.isEmpty()) { // list form [{label, confidence}] – take highest
            JsonNode best = null;
            for (JsonNode x : n) {
                if (best == null || x.path("confidence").asDouble() > best.path("confidence").asDouble()) {
                    best = x;
                }
            }
            return parseLabel(best);
        }
        return new LabelPrediction(n.path("label").asText(), n.has("confidence") ? n.get("confidence").asDouble() : null, null, null);
    }

    static List<Span> spans(JsonNode entities, String label) {
        List<Span> out = new ArrayList<>();
        for (JsonNode e : entities.path(label)) {
            if (e.isTextual()) {
                out.add(new Span(-1, -1, e.asText(), null));
            } else {
                out.add(new Span(e.path("start").asInt(-1), e.path("end").asInt(-1), e.path("text").asText(),
                        e.has("confidence") ? e.get("confidence").asDouble() : null));
            }
        }
        return out;
    }

    static Extraction parseEntities(JsonNode entities) {
        Comparator<Span> byConf = Comparator.comparing(s -> s.confidence() == null ? 0.0 : s.confidence());
        Span orderId = spans(entities, "order_id").stream().max(byConf).orElse(null);
        Span total = spans(entities, "total_amount").stream().max(byConf).orElse(null);
        List<Span> products = spans(entities, "product");
        List<Span> quantities = spans(entities, "quantity");
        products.sort(Comparator.comparingInt(Span::start));
        // pass 1: a quantity directly preceding a product ("two lattes") belongs to that product
        java.util.Map<Span, Span> pairs = new java.util.HashMap<>();
        List<Span> used = new ArrayList<>();
        for (Span p : products) {
            quantities.stream().filter(x -> !used.contains(x) && x.end() <= p.start() && p.start() - x.end() <= PAIR_GAP)
                    .max(Comparator.comparingInt(Span::end))
                    .ifPresent(q -> {
                        pairs.put(p, q);
                        used.add(q);
                    });
        }
        // pass 2: remaining products may take a quantity directly following them ("latte x2")
        for (Span p : products) {
            if (pairs.containsKey(p)) {
                continue;
            }
            quantities.stream().filter(x -> !used.contains(x) && x.start() >= p.end() && x.start() - p.end() <= PAIR_GAP)
                    .min(Comparator.comparingInt(Span::start))
                    .ifPresent(q -> {
                        pairs.put(p, q);
                        used.add(q);
                    });
        }
        List<Extraction.LineItem> items = new ArrayList<>();
        for (Span p : products) {
            Span q = pairs.get(p);
            items.add(new Extraction.LineItem(p.text(), q == null ? null : q.text(), p));
        }
        return new Extraction(orderId == null ? null : orderId.text(), orderId, items, total == null ? null : total.text(), total, null);
    }
}

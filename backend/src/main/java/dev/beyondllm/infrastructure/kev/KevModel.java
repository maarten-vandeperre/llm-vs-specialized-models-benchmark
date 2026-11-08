package dev.beyondllm.infrastructure.kev;

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
import dev.beyondllm.domain.ProductCatalog;
import dev.beyondllm.domain.TaskType;
import dev.beyondllm.domain.Timing;
import dev.beyondllm.infrastructure.http.JsonHttp;
import dev.beyondllm.infrastructure.ollama.OllamaAdminClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Kev decision model via Ollama's native typed decision protocol {@code POST /v1/systemone}.
 * <p>
 * Deliberately NOT a LangChain4j ChatModel: System One is not a chat API. One request carries a shared {@code state}
 * and up to 64 typed questions; the answer contains a probability distribution per question.
 * <p>
 * Two benchmark candidates are created from this class:
 * <ul>
 *     <li>{@code kev-*}: classification only (Kev cannot generate arbitrary strings, so extraction is UNSUPPORTED)</li>
 *     <li>{@code kev-*-candsel}: separately labeled candidate-selection experiment, where a regex stage proposes
 *     order-id / amount candidates and Kev only selects among them (plus closed-set product quantities).</li>
 * </ul>
 */
public class KevModel implements BenchmarkModel {

    public static final String MIN_OLLAMA = "0.40.4";
    static final int MAX_QTY = 6;

    private final String id;
    private final String tag;
    private final boolean candidateSelection;
    private final OllamaAdminClient admin;
    private final JsonHttp http;
    private final Duration timeout;
    private final String keepAlive;

    public KevModel(String id, String tag, boolean candidateSelection, OllamaAdminClient admin, Duration timeout, String keepAlive) {
        this.id = id;
        this.tag = tag;
        this.candidateSelection = candidateSelection;
        this.admin = admin;
        this.http = new JsonHttp(Duration.ofSeconds(5));
        this.timeout = timeout;
        this.keepAlive = keepAlive;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public ModelInfo info() {
        return new ModelInfo(id, candidateSelection ? "Kev " + sizeOf(tag) + " (candidate selection)" : "Kev " + sizeOf(tag),
                "model", "ollama /v1/systemone", tag, "decision model (typed choice questions)",
                tag.contains("bf16") ? "BF16" : tag.contains("q4") ? "Q4_K_M" : tag.contains("q8") ? "Q8_0" : null,
                "ollama", candidateSelection
                ? "Regex candidate stage + Kev selection. Kev never generates strings; it only selects candidates or closed-set quantities."
                : "Native probability distributions. 'confidence' = 1 - H(p)/ln N (concentration, not calibrated correctness).");
    }

    private static String sizeOf(String tag) {
        int i = tag.indexOf(':');
        return i < 0 ? tag : tag.substring(i + 1).split("-")[0];
    }

    @Override
    public ModelCapabilities capabilities() {
        EnumSet<Capability> f = EnumSet.of(Capability.CLASSIFICATION, Capability.PROBABILITY_DISTRIBUTION, Capability.LABEL_CONFIDENCE,
                Capability.COMBINED_SINGLE_CALL);
        EnumSet<TaskType> tasks = EnumSet.of(TaskType.CLASSIFICATION);
        if (candidateSelection) {
            f.add(Capability.CANDIDATE_SELECTION);
            f.add(Capability.PIPELINE);
            tasks.add(TaskType.EXTRACTION);
            tasks.add(TaskType.COMBINED);
        }
        return new ModelCapabilities(f, tasks, 73_728);
    }

    @Override
    public HealthStatus health() {
        HealthStatus h = admin.health(id, tag, MIN_OLLAMA);
        if (!h.isUp()) {
            return h;
        }
        return admin.show(tag).map(show -> {
            JsonNode caps = show.path("capabilities");
            boolean decision = false;
            for (JsonNode c : caps) {
                decision |= "decision".equals(c.asText());
            }
            return decision ? h : new HealthStatus(id, HealthStatus.State.RUNTIME_UNSUPPORTED,
                    "Model does not advertise the 'decision' capability", h.info());
        }).orElse(h);
    }

    @Override
    public List<String> runtimeModels() {
        return List.of(tag);
    }

    @Override
    public boolean unload() {
        Map<String, Object> body = Map.of("model", tag, "state", "unload", "keep_alive", 0,
                "questions", Map.of("q", Map.of("type", "noul", "instructions", "Is this text empty?")));
        return http.postJson(admin.baseUrl() + "/v1/systemone", body, timeout).ok();
    }

    @Override
    public BenchmarkResponse execute(BenchmarkRequest req) {
        if (!capabilities().supports(req.task())) {
            return BenchmarkResponse.unsupported(id, req, "Kev cannot generate arbitrary strings; task " + req.task()
                    + " requires extraction. Use the '-candsel' candidate-selection variant.");
        }
        long t0 = System.nanoTime();
        Map<String, Long> stages = new LinkedHashMap<>();
        Map<String, Object> questions = new LinkedHashMap<>();
        if (req.task().needsClassification()) {
            for (String field : LabelSchema.FIELDS) {
                questions.put(field, choice(instructionFor(field), LabelSchema.descriptions(field)));
            }
        }
        CandidateMaps cand = null;
        if (req.task().needsExtraction()) {
            long c0 = System.nanoTime();
            cand = addExtractionQuestions(req.text(), questions);
            stages.put("candidate_extraction", System.nanoTime() - c0);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", tag);
        body.put("state", Map.of("customer_message", req.text()));
        body.put("questions", questions);
        body.put("keep_alive", keepAlive);

        JsonHttp.Result r = http.postJson(admin.baseUrl() + "/v1/systemone", body, timeout);
        stages.put("kev_inference", r.nanos());
        if (r.failure() == JsonHttp.Failure.TIMEOUT) {
            return BenchmarkResponse.failure(id, req, Outcome.TIMEOUT, r.error(), Timing.client(System.nanoTime() - t0).withStages(stages), null);
        }
        if (!r.ok()) {
            Outcome o = r.status() == 400 && r.body() != null && r.body().contains("context") ? Outcome.UNSUPPORTED : Outcome.INFRA_ERROR;
            return BenchmarkResponse.failure(id, req, o, r.errorMessage(), Timing.client(System.nanoTime() - t0).withStages(stages), r.body());
        }
        try {
            JsonNode root = r.json();
            JsonNode answers = root.path("answers");
            if (!answers.isObject()) {
                throw new IllegalStateException("no 'answers' object");
            }
            Map<String, LabelPrediction> labels = new LinkedHashMap<>();
            if (req.task().needsClassification()) {
                for (String field : LabelSchema.FIELDS) {
                    labels.put(field, parseChoice(answers.path(field), null));
                }
            }
            Extraction extraction = cand == null ? null : parseExtraction(answers, cand);
            JsonNode usage = root.path("usage");
            Timing timing = new Timing(System.nanoTime() - t0, null, null,
                    usage.has("input_tokens") ? usage.get("input_tokens").asInt() : null,
                    usage.has("output_tokens") ? usage.get("output_tokens").asInt() : null, null, stages);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("questions", questions.size());
            if (cand != null) {
                meta.put("orderIdCandidates", cand.orderIds.values());
                meta.put("amountCandidates", cand.amounts.values());
            }
            return BenchmarkResponse.success(id, req, Prediction.of(labels, extraction), timing, r.body(), meta);
        } catch (Exception e) {
            return BenchmarkResponse.failure(id, req, Outcome.MALFORMED, "Unparseable System One response: " + e.getMessage(),
                    Timing.client(System.nanoTime() - t0).withStages(stages), r.body());
        }
    }

    // ---- request building -------------------------------------------------------------------------------------

    static String instructionFor(String field) {
        return switch (field) {
            case LabelSchema.INTENT -> "What does the customer want the coffee shop to do? If the final request contradicts an earlier one, the final request counts.";
            case LabelSchema.SENTIMENT -> "What is the customer's overall sentiment?";
            case LabelSchema.CATEGORY -> "Which topic does the message mainly concern?";
            case LabelSchema.COMPLAINT -> "Which problem, if any, does the customer complain about?";
            default -> throw new IllegalArgumentException(field);
        };
    }

    static Map<String, Object> choice(String instructions, Map<String, String> criteria) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("type", "choice");
        q.put("instructions", instructions);
        q.put("criteria", new LinkedHashMap<>(criteria));
        return q;
    }

    record CandidateMaps(Map<String, String> orderIds, Map<String, String> amounts) {
    }

    static CandidateMaps addExtractionQuestions(String text, Map<String, Object> questions) {
        CandidateExtractor.Candidates c = CandidateExtractor.extract(text);
        Map<String, String> ids = new LinkedHashMap<>();
        Map<String, String> criteria = new LinkedHashMap<>();
        for (int i = 0; i < c.orderIds().size(); i++) {
            ids.put("c" + (i + 1), c.orderIds().get(i));
            criteria.put("c" + (i + 1), "The order ID is \"" + c.orderIds().get(i) + "\"");
        }
        if (!ids.isEmpty()) {
            criteria.put(LabelSchema.NONE, "None of these is the customer's order ID.");
            questions.put("order_id", choice("Which candidate is the customer's order ID?", criteria));
        }
        Map<String, String> amounts = new LinkedHashMap<>();
        Map<String, String> aCrit = new LinkedHashMap<>();
        for (int i = 0; i < c.amounts().size(); i++) {
            amounts.put("a" + (i + 1), c.amounts().get(i));
            aCrit.put("a" + (i + 1), "The order total is \"" + c.amounts().get(i) + "\"");
        }
        if (!amounts.isEmpty()) {
            aCrit.put(LabelSchema.NONE, "None of these is the total amount of the order.");
            questions.put("total", choice("Which candidate is the total amount paid for the order?", aCrit));
        }
        Map<String, String> cur = new LinkedHashMap<>();
        cur.put("EUR", "Euro (€, EUR, euro)");
        cur.put("USD", "US dollar ($, USD, dollar)");
        cur.put("GBP", "British pound (£, GBP, pound)");
        cur.put(LabelSchema.NONE, "No amount or currency is mentioned.");
        questions.put("currency", choice("In which currency is the order total expressed?", cur));
        for (ProductCatalog.Product p : ProductCatalog.all()) {
            Map<String, String> q = new LinkedHashMap<>();
            q.put("0", "Not ordered or not mentioned as part of the order");
            for (int n = 1; n <= MAX_QTY; n++) {
                q.put(String.valueOf(n), n + (n == MAX_QTY ? " or more" : "") + " ordered");
            }
            String forms = String.join(", ", new LinkedHashSet<>(List.of(p.singular("en"), p.plural("en"), p.singular("nl"),
                    p.singular("fr"))));
            questions.put("qty_" + p.id(), choice("How many " + p.singular("en") + " (" + forms
                    + ") are part of the customer's order?", q));
        }
        return new CandidateMaps(ids, amounts);
    }

    // ---- response parsing -------------------------------------------------------------------------------------

    static LabelPrediction parseChoice(JsonNode a, Map<String, String> keyToValue) {
        if (a == null || a.isMissingNode() || !a.has("choice")) {
            throw new IllegalStateException("missing choice answer");
        }
        String choice = a.get("choice").asText();
        Map<String, Double> dist = new LinkedHashMap<>();
        a.path("probabilities").properties().forEach(e -> dist.put(
                keyToValue != null ? keyToValue.getOrDefault(e.getKey(), e.getKey()) : e.getKey(), e.getValue().asDouble()));
        String label = keyToValue != null ? keyToValue.getOrDefault(choice, choice) : choice;
        Double p = dist.get(label);
        Double conf = a.has("confidence") ? a.get("confidence").asDouble() : null;
        return new LabelPrediction(label, p, dist.isEmpty() ? null : dist, conf);
    }

    static Extraction parseExtraction(JsonNode answers, CandidateMaps cand) {
        String orderId = null;
        if (answers.has("order_id")) {
            String k = answers.path("order_id").path("choice").asText();
            orderId = cand.orderIds.get(k);
        }
        String total = null;
        if (answers.has("total")) {
            String k = answers.path("total").path("choice").asText();
            total = cand.amounts.get(k);
        }
        String currency = answers.path("currency").path("choice").asText(null);
        if (LabelSchema.NONE.equals(currency)) {
            currency = null;
        }
        List<Extraction.LineItem> items = new ArrayList<>();
        for (ProductCatalog.Product p : ProductCatalog.all()) {
            JsonNode q = answers.path("qty_" + p.id());
            String qty = q.path("choice").asText("0");
            if (!"0".equals(qty)) {
                items.add(new Extraction.LineItem(p.id(), qty, null));
            }
        }
        return new Extraction(orderId, null, items, total, null, currency);
    }
}

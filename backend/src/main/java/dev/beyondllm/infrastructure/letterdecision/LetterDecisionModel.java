package dev.beyondllm.infrastructure.letterdecision;

import com.fasterxml.jackson.databind.JsonNode;
import dev.beyondllm.application.BenchmarkModel;
import dev.beyondllm.application.ModelInfo;
import dev.beyondllm.domain.BenchmarkRequest;
import dev.beyondllm.domain.BenchmarkResponse;
import dev.beyondllm.domain.Capability;
import dev.beyondllm.domain.HealthStatus;
import dev.beyondllm.domain.LabelPrediction;
import dev.beyondllm.domain.LabelSchema;
import dev.beyondllm.domain.ModelCapabilities;
import dev.beyondllm.domain.Outcome;
import dev.beyondllm.domain.Prediction;
import dev.beyondllm.domain.Timing;
import dev.beyondllm.infrastructure.http.JsonHttp;
import dev.beyondllm.infrastructure.kev.KevModel;
import dev.beyondllm.infrastructure.ollama.OllamaAdminClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a general instruction-tuned LLM (here Gemma 4 12B) into a decision model, without any fine-tuning.
 * <p>
 * This is the method the community {@code aminroudaki/decisio-gemma} Ollama model describes (unmodified Gemma 4 12B,
 * "Answer with the letter only", softmax over the option letters). That model's manifest is rejected by Ollama 0.40.x
 * ("downloaded in a format that is no longer supported"), so the benchmark applies the same method itself on the
 * official {@code gemma4:12b} weights via Ollama {@code /api/chat}:
 * <ul>
 *     <li>one call per label field; options are labelled A, B, C, ... with the same descriptions Kev receives;</li>
 *     <li>the customer message comes first, so the four calls of one request share a prompt prefix;</li>
 *     <li>{@code num_predict=1}, {@code temperature=0}, thinking off; the label probabilities are the softmax of the
 *     option letters' log-probabilities of that single token, i.e. the model's own token probabilities, never numbers
 *     the model writes. Letters missing from the top log-probabilities get probability 0.</li>
 * </ul>
 * Classification only: like Kev, this candidate cannot extract strings.
 */
public class LetterDecisionModel implements BenchmarkModel {

    static final String SYSTEM = "Answer with the letter only.";
    static final int TOP_LOGPROBS = 20;
    static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private final String id;
    private final String displayName;
    private final String tag;
    private final OllamaAdminClient admin;
    private final JsonHttp http;
    private final Duration timeout;
    private final String keepAlive;
    private final int numCtx;

    public LetterDecisionModel(String id, String displayName, String tag, OllamaAdminClient admin, Duration timeout,
                               String keepAlive, int numCtx) {
        this.id = id;
        this.displayName = displayName;
        this.tag = tag;
        this.admin = admin;
        this.http = new JsonHttp(Duration.ofSeconds(5));
        this.timeout = timeout;
        this.keepAlive = keepAlive;
        this.numCtx = numCtx;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public ModelInfo info() {
        return new ModelInfo(id, displayName, "model", "ollama /api/chat (letter log-probabilities)", tag,
                "decision model (general LLM + letter-probability decision layer)", null, "ollama",
                "One call per label field, options lettered A.., num_predict=1, temperature=0, think=false, num_ctx=" + numCtx
                        + ", truncate=false. Probabilities = softmax over the option letters' logprobs (top " + TOP_LOGPROBS
                        + "). 'confidence' = 1 - H(p)/ln N, as for Kev.");
    }

    @Override
    public ModelCapabilities capabilities() {
        return new ModelCapabilities(EnumSet.of(Capability.CLASSIFICATION, Capability.PROBABILITY_DISTRIBUTION,
                Capability.LABEL_CONFIDENCE), EnumSet.of(dev.beyondllm.domain.TaskType.CLASSIFICATION), numCtx);
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

    @Override
    public BenchmarkResponse execute(BenchmarkRequest req) {
        if (!capabilities().supports(req.task())) {
            return BenchmarkResponse.unsupported(id, req, displayName + " is used as a decision model and cannot generate "
                    + "arbitrary strings; task " + req.task() + " requires extraction.");
        }
        long t0 = System.nanoTime();
        Map<String, Long> stages = new LinkedHashMap<>();
        Map<String, LabelPrediction> labels = new LinkedHashMap<>();
        List<String> raws = new ArrayList<>();
        long server = 0;
        long load = 0;
        int inTokens = 0;
        int outTokens = 0;
        for (String field : LabelSchema.FIELDS) {
            List<String> keys = new ArrayList<>(LabelSchema.descriptions(field).keySet());
            JsonHttp.Result r = http.postJson(admin.baseUrl() + "/api/chat", body(req.text(), field), timeout);
            stages.put(field, r.nanos());
            if (r.body() != null) {
                raws.add(r.body());
            }
            String raw = "[" + String.join(",", raws) + "]";
            if (r.failure() == JsonHttp.Failure.TIMEOUT) {
                return BenchmarkResponse.failure(id, req, Outcome.TIMEOUT, r.error(), Timing.client(System.nanoTime() - t0).withStages(stages), raw);
            }
            if (!r.ok()) {
                Outcome o = r.status() == 400 && r.body() != null && r.body().contains("context") ? Outcome.UNSUPPORTED : Outcome.INFRA_ERROR;
                return BenchmarkResponse.failure(id, req, o, r.errorMessage(), Timing.client(System.nanoTime() - t0).withStages(stages), raw);
            }
            try {
                JsonNode root = r.json();
                labels.put(field, parse(root, keys));
                server += root.path("total_duration").asLong(0);
                load += root.path("load_duration").asLong(0);
                inTokens += root.path("prompt_eval_count").asInt(0);
                outTokens += root.path("eval_count").asInt(0);
            } catch (Exception e) {
                return BenchmarkResponse.failure(id, req, Outcome.MALFORMED, "Unusable letter answer for " + field + ": " + e.getMessage(),
                        Timing.client(System.nanoTime() - t0).withStages(stages), raw);
            }
        }
        Timing timing = new Timing(System.nanoTime() - t0, server, load, inTokens, outTokens, null, stages);
        return BenchmarkResponse.success(id, req, Prediction.of(labels, null), timing, "[" + String.join(",", raws) + "]",
                Map.of("calls", LabelSchema.FIELDS.size()));
    }

    // ---- request building -------------------------------------------------------------------------------------

    Map<String, Object> body(String text, String field) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", tag);
        body.put("stream", false);
        body.put("think", false);
        body.put("logprobs", true);
        body.put("top_logprobs", TOP_LOGPROBS);
        body.put("keep_alive", keepAlive);
        body.put("truncate", false);
        body.put("options", Map.of("temperature", 0.0, "seed", 42, "num_ctx", numCtx, "num_predict", 1));
        body.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM),
                Map.of("role", "user", "content", prompt(text, field))));
        return body;
    }

    static String prompt(String text, String field) {
        StringBuilder sb = new StringBuilder();
        sb.append("Customer message:\n\"\"\"\n").append(text).append("\n\"\"\"\n\n");
        sb.append("Question: ").append(KevModel.instructionFor(field)).append('\n');
        int i = 0;
        for (Map.Entry<String, String> e : LabelSchema.descriptions(field).entrySet()) {
            sb.append(LETTERS.charAt(i++)).append(") ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        }
        sb.append("\nAnswer with the letter of the best option.");
        return sb.toString();
    }

    // ---- response parsing -------------------------------------------------------------------------------------

    static LabelPrediction parse(JsonNode root, List<String> keys) {
        JsonNode first = root.path("logprobs").path(0);
        JsonNode top = first.path("top_logprobs");
        if (!top.isArray() || top.isEmpty()) {
            throw new IllegalStateException("no top_logprobs in response");
        }
        double[] logp = new double[keys.size()];
        boolean[] seen = new boolean[keys.size()];
        for (JsonNode t : top) {
            String tok = t.path("token").asText().strip();
            if (tok.length() != 1) {
                continue;
            }
            int idx = LETTERS.indexOf(tok.charAt(0));
            if (idx >= 0 && idx < keys.size() && !seen[idx]) {
                seen[idx] = true;
                logp[idx] = t.path("logprob").asDouble();
            }
        }
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < keys.size(); i++) {
            if (seen[i]) {
                max = Math.max(max, logp[i]);
            }
        }
        if (max == Double.NEGATIVE_INFINITY) {
            throw new IllegalStateException("no option letter among the top " + top.size() + " tokens (generated '"
                    + root.path("message").path("content").asText() + "')");
        }
        double sum = 0;
        double[] p = new double[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            p[i] = seen[i] ? Math.exp(logp[i] - max) : 0.0;
            sum += p[i];
        }
        Map<String, Double> dist = new LinkedHashMap<>();
        int best = 0;
        double entropy = 0;
        for (int i = 0; i < keys.size(); i++) {
            p[i] /= sum;
            dist.put(keys.get(i), p[i]);
            if (p[i] > p[best]) {
                best = i;
            }
            if (p[i] > 0) {
                entropy -= p[i] * Math.log(p[i]);
            }
        }
        double confidence = keys.size() > 1 ? 1 - entropy / Math.log(keys.size()) : 1.0;
        return new LabelPrediction(keys.get(best), p[best], dist, confidence);
    }
}

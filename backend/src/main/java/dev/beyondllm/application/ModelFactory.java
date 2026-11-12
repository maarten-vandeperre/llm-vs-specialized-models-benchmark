package dev.beyondllm.application;

import dev.beyondllm.domain.TaskType;
import dev.beyondllm.infrastructure.cloud.CloudLlmModel;
import dev.beyondllm.infrastructure.gliner.GlinerModel;
import dev.beyondllm.infrastructure.hybrid.HybridPipelineModel;
import dev.beyondllm.infrastructure.hybrid.HybridThresholds;
import dev.beyondllm.infrastructure.kev.KevModel;
import dev.beyondllm.infrastructure.letterdecision.LetterDecisionModel;
import dev.beyondllm.infrastructure.nuextract.NuExtractModel;
import dev.beyondllm.infrastructure.ollama.OllamaAdminClient;
import dev.beyondllm.infrastructure.qwen.QwenModel;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds all benchmark candidates from configuration (composition root). Adapters stay framework-free plain classes;
 * only this factory knows about configuration and wiring.
 */
@ApplicationScoped
public class ModelFactory {

    private final OllamaAdminClient ollama;
    private final HybridThresholds thresholds;
    private final Duration timeout;
    private final String keepAlive;
    private final int numCtx;
    private final boolean think;
    private final String glinerUrl;
    private final String glinerSmallUrl;
    private final String glinerMultiUrl;
    private final String glinerDecideUrl;
    private final double glinerThreshold;
    private final Optional<List<String>> enabled;
    private final String kevTag;
    private final String kevLargeTag;
    private final String gemmaDecisionTag;
    private final String nuextractTag;
    private final String qwenTag;
    private final String openaiKey;
    private final String openaiUrl;
    private final String anthropicKey;
    private final String anthropicUrl;

    public ModelFactory(OllamaAdminClient ollama, HybridThresholds thresholds,
                        @ConfigProperty(name = "bench.timeout-seconds", defaultValue = "120") int timeoutSeconds,
                        @ConfigProperty(name = "bench.ollama.keep-alive", defaultValue = "30m") String keepAlive,
                        @ConfigProperty(name = "bench.ollama.num-ctx", defaultValue = "16384") int numCtx,
                        @ConfigProperty(name = "bench.generative.think", defaultValue = "false") boolean think,
                        @ConfigProperty(name = "bench.gliner.base-url") String glinerUrl,
                        @ConfigProperty(name = "bench.gliner.small-url") Optional<String> glinerSmallUrl,
                        @ConfigProperty(name = "bench.gliner.multi-url") Optional<String> glinerMultiUrl,
                        @ConfigProperty(name = "bench.gliner.decide-url") Optional<String> glinerDecideUrl,
                        @ConfigProperty(name = "bench.gliner.threshold", defaultValue = "0.5") double glinerThreshold,
                        @ConfigProperty(name = "bench.models.enabled") Optional<List<String>> enabled,
                        @ConfigProperty(name = "bench.models.kev.tag", defaultValue = "kev:0.8b-mlx-bf16") String kevTag,
                        @ConfigProperty(name = "bench.models.kev-large.tag", defaultValue = "kev:4b-mlx-bf16") String kevLargeTag,
                        @ConfigProperty(name = "bench.models.gemma-decision.tag", defaultValue = "gemma4:12b") String gemmaDecisionTag,
                        @ConfigProperty(name = "bench.models.nuextract.tag", defaultValue = "numind/nuextract3:q4_k_m") String nuextractTag,
                        @ConfigProperty(name = "bench.models.qwen.tag", defaultValue = "qwen3.5:4b") String qwenTag,
                        @ConfigProperty(name = "bench.openai.api-key") Optional<String> openaiKey,
                        @ConfigProperty(name = "bench.openai.base-url", defaultValue = "https://api.openai.com") String openaiUrl,
                        @ConfigProperty(name = "bench.anthropic.api-key") Optional<String> anthropicKey,
                        @ConfigProperty(name = "bench.anthropic.base-url", defaultValue = "https://api.anthropic.com") String anthropicUrl) {
        this.ollama = ollama;
        this.thresholds = thresholds;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.keepAlive = keepAlive;
        this.numCtx = numCtx;
        this.think = think;
        this.glinerUrl = glinerUrl;
        this.glinerSmallUrl = glinerSmallUrl.orElse(glinerUrl);
        this.glinerMultiUrl = glinerMultiUrl.orElse(glinerUrl);
        this.glinerDecideUrl = glinerDecideUrl.orElse(glinerUrl);
        this.glinerThreshold = glinerThreshold;
        this.enabled = enabled;
        this.kevTag = kevTag;
        this.kevLargeTag = kevLargeTag;
        this.gemmaDecisionTag = gemmaDecisionTag;
        this.nuextractTag = nuextractTag;
        this.qwenTag = qwenTag;
        this.openaiKey = openaiKey.orElse("");
        this.openaiUrl = openaiUrl;
        this.anthropicKey = anthropicKey.orElse("");
        this.anthropicUrl = anthropicUrl;
    }

    public List<BenchmarkModel> create() {
        Map<String, BenchmarkModel> m = new LinkedHashMap<>();
        EnumSet<TaskType> all = EnumSet.allOf(TaskType.class);
        put(m, new KevModel("kev-0.8b", kevTag, false, ollama, timeout, keepAlive));
        put(m, new KevModel("kev-0.8b-candsel", kevTag, true, ollama, timeout, keepAlive));
        put(m, new KevModel("kev-4b", kevLargeTag, false, ollama, timeout, keepAlive));
        // Gemma 4 12B used as a decision model: letter log-probabilities, one call per label field (classification only).
        put(m, new LetterDecisionModel("gemma4-12b-decision", "Gemma 4 12B (decision, letter probabilities)", gemmaDecisionTag,
                ollama, timeout, keepAlive, numCtx));
        put(m, new GlinerModel("gliner2.5-base", "fastino/gliner2.5-base-v1", "GLiNER2.5 base (194M)", all, false, glinerUrl, timeout, glinerThreshold));
        put(m, new GlinerModel("gliner2.5-small", "fastino/gliner2.5-small-v1", "GLiNER2.5 small (74M)", all, false, glinerSmallUrl, timeout, glinerThreshold));
        put(m, new GlinerModel("gliner2.5-multi", "fastino/gliner2.5-multi-v1", "GLiNER2.5 multi (287M)", all, true, glinerMultiUrl, timeout, glinerThreshold));
        put(m, new GlinerModel("gliner2.5-decide", "fastino/GLiNER2.5-Decide", "GLiNER2.5-Decide (340M)",
                EnumSet.of(TaskType.CLASSIFICATION), false, glinerDecideUrl, timeout, glinerThreshold));
        put(m, new NuExtractModel("nuextract3", nuextractTag, ollama, timeout, numCtx, keepAlive, think));
        put(m, new QwenModel("qwen3.5-4b", qwenTag, ollama, timeout, numCtx, think));

        // Optional commercial API baselines: opt-in (DISABLED without a key, never part of implicit selections).
        // Each runs at the lowest reasoning setting the model accepts.
        put(m, new CloudLlmModel("openai-gpt-6-luna", "OpenAI GPT-6 Luna (simple)", CloudLlmModel.Provider.OPENAI,
                "gpt-6-luna", openaiKey, openaiUrl, "none", false, timeout));
        put(m, new CloudLlmModel("openai-gpt-6.1-sol", "OpenAI GPT-6.1 Sol (complex)", CloudLlmModel.Provider.OPENAI,
                "gpt-6.1-sol", openaiKey, openaiUrl, "low", false, timeout));
        put(m, new CloudLlmModel("claude-haiku-5.5", "Claude Haiku 5.5 (simple)", CloudLlmModel.Provider.ANTHROPIC,
                "claude-haiku-5-5", anthropicKey, anthropicUrl, "low", true, timeout));
        put(m, new CloudLlmModel("claude-opus-5.5", "Claude Opus 5.5 (complex)", CloudLlmModel.Provider.ANTHROPIC,
                "claude-opus-5-5", anthropicKey, anthropicUrl, "low", false, timeout));

        BenchmarkModel gliner = m.get("gliner2.5-base");
        BenchmarkModel kev = m.get("kev-0.8b");
        BenchmarkModel qwen = m.get("qwen3.5-4b");
        put(m, new HybridPipelineModel("hybrid-gliner-kev-qwen", HybridPipelineModel.Strategy.GLINER_KEV_FALLBACK, gliner, kev, qwen, thresholds));
        put(m, new HybridPipelineModel("hybrid-gliner-kev", HybridPipelineModel.Strategy.GLINER_KEV_FALLBACK, gliner, kev, null, thresholds));
        put(m, new HybridPipelineModel("hybrid-gliner-qwen", HybridPipelineModel.Strategy.GLINER_COMBINED_FALLBACK, gliner, null, qwen, thresholds));

        List<BenchmarkModel> out = new ArrayList<>();
        for (BenchmarkModel b : m.values()) {
            if (enabled.isEmpty() || enabled.get().contains(b.id())) {
                out.add(b);
            }
        }
        return out;
    }

    private static void put(Map<String, BenchmarkModel> m, BenchmarkModel b) {
        m.put(b.id(), b);
    }
}

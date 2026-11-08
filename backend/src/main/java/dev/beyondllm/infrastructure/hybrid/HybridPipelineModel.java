package dev.beyondllm.infrastructure.hybrid;

import dev.beyondllm.application.BenchmarkModel;
import dev.beyondllm.application.ModelInfo;
import dev.beyondllm.domain.BenchmarkRequest;
import dev.beyondllm.domain.BenchmarkResponse;
import dev.beyondllm.domain.Capability;
import dev.beyondllm.domain.DecisionPolicy;
import dev.beyondllm.domain.Extraction;
import dev.beyondllm.domain.HealthStatus;
import dev.beyondllm.domain.LabelPrediction;
import dev.beyondllm.domain.LabelSchema;
import dev.beyondllm.domain.ModelCapabilities;
import dev.beyondllm.domain.Outcome;
import dev.beyondllm.domain.Prediction;
import dev.beyondllm.domain.TaskType;
import dev.beyondllm.domain.Timing;
import dev.beyondllm.domain.ValueNormalizer;
import dev.beyondllm.infrastructure.http.JsonHttp;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Hybrid pipelines exposed as ordinary benchmark candidates. End-to-end latency is the wall-clock time of the whole
 * pipeline (all stages, orchestration and fallback calls), so it can be compared fairly with single-model candidates.
 *
 * <ul>
 *   <li>{@link Strategy#GLINER_KEV_FALLBACK}: GLiNER extraction → Kev classification → business decision; if Kev's
 *   probability for intent or complaint is below the threshold (or a stage fails), the LLM classifies instead.</li>
 *   <li>{@link Strategy#GLINER_COMBINED_FALLBACK}: one GLiNER multi-task call; if its intent/complaint confidence is
 *   below the threshold (or it fails / input unsupported), the whole request is sent to the LLM.</li>
 * </ul>
 */
public class HybridPipelineModel implements BenchmarkModel {

    public enum Strategy { GLINER_KEV_FALLBACK, GLINER_COMBINED_FALLBACK }

    private static final List<String> ROUTING_FIELDS = List.of(LabelSchema.INTENT, LabelSchema.COMPLAINT);

    private final String id;
    private final Strategy strategy;
    private final BenchmarkModel gliner;
    private final BenchmarkModel kev;
    private final BenchmarkModel fallback;
    private final HybridThresholds thresholds;

    public HybridPipelineModel(String id, Strategy strategy, BenchmarkModel gliner, BenchmarkModel kev, BenchmarkModel fallback,
                               HybridThresholds thresholds) {
        this.id = id;
        this.strategy = strategy;
        this.gliner = gliner;
        this.kev = kev;
        this.fallback = fallback;
        this.thresholds = thresholds;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public ModelInfo info() {
        String chain = strategy == Strategy.GLINER_KEV_FALLBACK
                ? gliner.id() + " (extract) → " + kev.id() + " (classify) → decision" + (fallback != null ? " → " + fallback.id() + " fallback" : "")
                : gliner.id() + " (combined) → decision" + (fallback != null ? " → " + fallback.id() + " fallback" : "");
        return new ModelInfo(id, strategy == Strategy.GLINER_KEV_FALLBACK ? "Hybrid GLiNER+Kev" + (fallback != null ? "+LLM" : "")
                : "Hybrid GLiNER" + (fallback != null ? "+LLM" : ""), "pipeline", "pipeline", chain, "hybrid pipeline", null, null,
                "Routing threshold source: " + thresholds.source());
    }

    @Override
    public ModelCapabilities capabilities() {
        return new ModelCapabilities(EnumSet.of(Capability.CLASSIFICATION, Capability.ARBITRARY_TEXT_EXTRACTION,
                Capability.STRUCTURED_EXTRACTION, Capability.LABEL_CONFIDENCE, Capability.OFFSETS, Capability.PIPELINE),
                EnumSet.allOf(TaskType.class), null);
    }

    @Override
    public HealthStatus health() {
        List<HealthStatus> parts = Stream.of(gliner, kev, fallback).filter(m -> m != null).map(BenchmarkModel::health).toList();
        for (HealthStatus h : parts) {
            if (!h.isUp()) {
                return new HealthStatus(id, h.state(), "Stage " + h.modelId() + ": " + h.detail(), Map.of());
            }
        }
        return HealthStatus.up(id, "all stages up", Map.of("stages", parts.stream().map(HealthStatus::modelId).toList()));
    }

    @Override
    public List<String> runtimeModels() {
        List<String> all = new ArrayList<>();
        Stream.of(gliner, kev, fallback).filter(m -> m != null).forEach(m -> all.addAll(m.runtimeModels()));
        return all;
    }

    public double threshold() {
        return strategy == Strategy.GLINER_KEV_FALLBACK ? thresholds.kev() : thresholds.gliner();
    }

    @Override
    public BenchmarkResponse execute(BenchmarkRequest req) {
        long t0 = System.nanoTime();
        Map<String, Long> stages = new LinkedHashMap<>();
        Map<String, String> raws = new LinkedHashMap<>();
        List<String> route = new ArrayList<>();
        Map<String, LabelPrediction> labels = new LinkedHashMap<>();
        Extraction extraction = null;
        boolean fallbackUsed = false;

        if (strategy == Strategy.GLINER_KEV_FALLBACK) {
            if (req.task().needsExtraction()) {
                BenchmarkResponse g = run(gliner, req.withTask(TaskType.EXTRACTION), stages, raws, route);
                if (!g.isSuccess()) {
                    return fail(req, g, stages, raws, route, t0);
                }
                extraction = g.prediction().extraction();
            }
            if (req.task().needsClassification()) {
                BenchmarkResponse k = run(kev, req.withTask(TaskType.CLASSIFICATION), stages, raws, route);
                boolean low = !k.isSuccess() || belowThreshold(k.prediction().labels(), thresholds.kev());
                if (low && fallback != null) {
                    BenchmarkResponse q = run(fallback, req.withTask(TaskType.CLASSIFICATION), stages, raws, route);
                    if (!q.isSuccess()) {
                        return fail(req, q, stages, raws, route, t0);
                    }
                    labels.putAll(q.prediction().labels());
                    fallbackUsed = true;
                } else if (!k.isSuccess()) {
                    return fail(req, k, stages, raws, route, t0);
                } else {
                    labels.putAll(k.prediction().labels());
                }
            }
        } else {
            BenchmarkResponse g = run(gliner, req, stages, raws, route);
            boolean low = !g.isSuccess() || (req.task().needsClassification() && belowThreshold(g.prediction().labels(), thresholds.gliner()));
            if (low && fallback != null) {
                BenchmarkResponse q = run(fallback, req, stages, raws, route);
                if (!q.isSuccess()) {
                    return fail(req, q, stages, raws, route, t0);
                }
                labels.putAll(q.prediction().labels());
                extraction = q.prediction().extraction();
                fallbackUsed = true;
            } else if (!g.isSuccess()) {
                return fail(req, g, stages, raws, route, t0);
            } else {
                labels.putAll(g.prediction().labels());
                extraction = g.prediction().extraction();
            }
        }

        long d0 = System.nanoTime();
        String decision = null;
        if (req.task() == TaskType.COMBINED) {
            ValueNormalizer.Money total = extraction == null ? null : ValueNormalizer.money(extraction.total());
            decision = DecisionPolicy.decide(labelOf(labels, LabelSchema.INTENT), labelOf(labels, LabelSchema.COMPLAINT),
                    extraction != null && ValueNormalizer.orderId(extraction.orderId()) != null, total == null ? null : total.amount());
        }
        stages.put("decision", System.nanoTime() - d0);
        Timing timing = new Timing(System.nanoTime() - t0, null, null, null, null, null, stages);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("fallbackUsed", fallbackUsed);
        meta.put("threshold", threshold());
        return BenchmarkResponse.success(id, req, new Prediction(labels, extraction, decision, route), timing, rawJson(raws), meta);
    }

    private static String labelOf(Map<String, LabelPrediction> labels, String f) {
        LabelPrediction p = labels.get(f);
        return p == null ? null : p.label();
    }

    static boolean belowThreshold(Map<String, LabelPrediction> labels, double threshold) {
        for (String f : ROUTING_FIELDS) {
            LabelPrediction p = labels.get(f);
            if (p == null || p.probability() == null || p.probability() < threshold) {
                return true;
            }
        }
        return false;
    }

    private BenchmarkResponse run(BenchmarkModel m, BenchmarkRequest r, Map<String, Long> stages, Map<String, String> raws, List<String> route) {
        long s = System.nanoTime();
        BenchmarkResponse resp = m.execute(r);
        String key = m.id() + (route.contains(m.id()) ? "#2" : "");
        stages.put(key, System.nanoTime() - s);
        raws.put(key, resp.rawResponse());
        route.add(m == fallback ? m.id() + " (fallback)" : m.id());
        return resp;
    }

    private BenchmarkResponse fail(BenchmarkRequest req, BenchmarkResponse stage, Map<String, Long> stages, Map<String, String> raws,
                                   List<String> route, long t0) {
        Outcome o = stage.outcome() == Outcome.SUCCESS ? Outcome.INFRA_ERROR : stage.outcome();
        return new BenchmarkResponse(id, req.requestId(), req.task(), o, null, "Stage " + route.getLast() + ": " + stage.error(),
                new Timing(System.nanoTime() - t0, null, null, null, null, null, stages), rawJson(raws), Map.of("route", route));
    }

    private static String rawJson(Map<String, String> raws) {
        try {
            return JsonHttp.MAPPER.writeValueAsString(raws);
        } catch (Exception e) {
            return String.valueOf(raws);
        }
    }
}

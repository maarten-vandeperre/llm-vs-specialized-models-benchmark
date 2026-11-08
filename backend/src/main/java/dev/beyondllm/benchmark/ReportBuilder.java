package dev.beyondllm.benchmark;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.beyondllm.domain.LabelSchema;
import dev.beyondllm.domain.Outcome;
import dev.beyondllm.domain.TaskType;
import dev.beyondllm.evaluation.Metrics;
import dev.beyondllm.evaluation.RecordEvaluation;
import dev.beyondllm.infrastructure.http.JsonHttp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Aggregates persisted predictions into the run report. Quality metrics use the "quality" phase only (one attempt per
 * record, concurrency 1). Failures are included as errors in every quality denominator.
 */
public final class ReportBuilder {

    /** Automation threshold at which risk/coverage is reported for models with native scores. */
    public static final double REPORT_THRESHOLD = 0.80;

    private ReportBuilder() {
    }

    public static Map<String, Object> build(RunStatus status, Map<String, Object> env, Map<String, Object> modelRunsIn,
                                            List<PredictionRecord> preds) {
        Map<String, Map<String, Object>> modelRuns = JsonHttp.MAPPER.convertValue(modelRunsIn, new TypeReference<>() {
        });
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("runId", status.runId());
        report.put("label", status.label());
        report.put("status", status.status());
        report.put("createdAt", status.createdAt());
        report.put("finishedAt", status.finishedAt());
        report.put("synthetic", true);
        report.put("disclaimer", "Synthetic, fictional coffee-shop dataset. Results do not generalize to production customers.");
        report.put("datasetVersion", status.config().dataset());
        report.put("environment", env);
        report.put("config", status.config());
        report.put("errors", status.errors());
        List<Map<String, Object>> models = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> e : modelRuns.entrySet()) {
            String id = e.getKey();
            List<PredictionRecord> mine = preds.stream().filter(p -> p.modelId().equals(id)).toList();
            models.add(model(id, e.getValue(), mine));
        }
        report.put("models", models);
        return report;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> model(String id, Map<String, Object> run, List<PredictionRecord> all) {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> info = (Map<String, Object>) run.getOrDefault("info", Map.of());
        m.put("modelId", id);
        m.put("displayName", info.get("displayName"));
        m.put("kind", info.get("kind"));
        m.put("runtime", info.get("runtime"));
        m.put("modelRef", info.get("modelRef"));
        m.put("quantization", info.get("quantization"));
        m.put("family", info.get("family"));
        m.put("notes", info.get("notes"));
        Map<String, Object> health = (Map<String, Object>) run.getOrDefault("health", Map.of());
        m.put("health", Map.of("state", String.valueOf(health.get("state")), "detail", String.valueOf(health.get("detail"))));
        m.put("skipped", run.get("skipped"));
        m.put("compatibleRecords", run.get("compatibleRecords"));
        m.put("incompatibleRecords", run.get("incompatibleRecords"));

        List<PredictionRecord> q = all.stream().filter(p -> PredictionRecord.QUALITY.equals(p.phase())).toList();
        m.put("counts", counts(q));
        m.put("classification", classification(q));
        m.put("extraction", extraction(q));
        m.put("decision", decision(q));
        m.put("abstention", abstention(q));
        m.put("calibration", calibration(q));

        // Headline latency: every evaluation input seen once (quality phase), successful, excluding full prompt-cache hits.
        List<PredictionRecord> qOk = q.stream().filter(p -> p.outcome() == Outcome.SUCCESS).toList();
        List<PredictionRecord> lat = qOk.stream().filter(p -> !PromptCache.fullHit(p.rawResponse())).toList();
        m.put("latency", Metrics.latency(lat.stream().map(p -> p.timing().clientNanos() / 1e6).toList()));
        // Repeated identical inputs (latency phase): reported separately because runtimes may serve them from a cache.
        List<PredictionRecord> rep = all.stream().filter(p -> PredictionRecord.LATENCY.equals(p.phase()) && p.outcome() == Outcome.SUCCESS).toList();
        m.put("repeatLatency", Metrics.latency(rep.stream().map(p -> p.timing().clientNanos() / 1e6).toList()));
        Map<String, Object> cache = new LinkedHashMap<>();
        cache.put("qualityFullHits", qOk.size() - lat.size());
        cache.put("repeatFullHits", rep.stream().filter(p -> PromptCache.fullHit(p.rawResponse())).count());
        cache.put("repeatRequests", rep.size());
        cache.put("note", "full hit = at most " + PromptCache.MAX_UNCACHED_TOKENS + " prompt tokens left uncached by Ollama (input seen before); excluded from headline and per-length latency");
        m.put("promptCache", cache);
        List<Double> server = lat.stream().map(p -> p.timing().serverNanos()).filter(Objects::nonNull).map(n -> n / 1e6).toList();
        m.put("serverLatency", server.size() == lat.size() && !server.isEmpty() ? Metrics.latency(server) : null);
        m.put("latencyByTask", latencyByTask(lat));
        m.put("coldStart", run.get("coldStart"));
        m.put("throughput", throughput(run, all));
        m.put("tokens", tokens(lat));
        m.put("resources", resources(run));
        m.put("perLanguage", breakdown(q, PredictionRecord::language));
        m.put("perGroup", breakdown(q, PredictionRecord::group));
        m.put("perLength", perLength(q));
        m.put("fallbackRate", fallbackRate(q));
        long attempts = all.stream().filter(p -> !PredictionRecord.WARMUP.equals(p.phase())).count();
        long infra = all.stream().filter(p -> !PredictionRecord.WARMUP.equals(p.phase())
                && (p.outcome() == Outcome.INFRA_ERROR || p.outcome() == Outcome.TIMEOUT)).count();
        m.put("infraFailureRate", attempts == 0 ? null : Metrics.round((double) infra / attempts));
        return m;
    }

    static Map<String, Object> counts(List<PredictionRecord> q) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("total", q.size());
        c.put("success", q.stream().filter(p -> p.outcome() == Outcome.SUCCESS).count());
        c.put("abstained", q.stream().filter(p -> p.outcome() == Outcome.ABSTAINED).count());
        c.put("unsupported", q.stream().filter(p -> p.outcome() == Outcome.UNSUPPORTED).count());
        c.put("malformed", q.stream().filter(p -> p.outcome() == Outcome.MALFORMED).count());
        c.put("timeout", q.stream().filter(p -> p.outcome() == Outcome.TIMEOUT).count());
        c.put("infraError", q.stream().filter(p -> p.outcome() == Outcome.INFRA_ERROR).count());
        c.put("malformedRate", Metrics.rate((long) c.get("malformed"), q.size()));
        c.put("timeoutRate", Metrics.rate((long) c.get("timeout"), q.size()));
        return c;
    }

    static Map<String, Object> classification(List<PredictionRecord> q) {
        List<PredictionRecord> cls = q.stream().filter(p -> p.evaluation() != null && p.evaluation().classification() != null).toList();
        if (cls.isEmpty()) {
            return null;
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        double accSum = 0, f1Sum = 0;
        for (String f : LabelSchema.FIELDS) {
            List<String> gold = new ArrayList<>();
            List<String> pred = new ArrayList<>();
            for (PredictionRecord p : cls) {
                RecordEvaluation.FieldEval fe = p.evaluation().classification().get(f);
                gold.add(fe.gold());
                pred.add(fe.predicted());
            }
            Metrics.ClassificationResult r = Metrics.classification(gold, pred, LabelSchema.labels(f));
            fields.put(f, r);
            accSum += r.accuracy();
            f1Sum += r.macroF1();
        }
        long allCorrect = cls.stream().filter(p -> p.evaluation().classification().values().stream().allMatch(RecordEvaluation.FieldEval::correct)).count();
        Metrics.ClassificationResult intent = (Metrics.ClassificationResult) fields.get(LabelSchema.INTENT);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("n", cls.size());
        out.put("accuracy", intent.accuracy());
        out.put("accuracyCi", intent.accuracyCi());
        out.put("macroF1", intent.macroF1());
        out.put("macroF1Ci", intent.macroF1Ci());
        out.put("headlineField", LabelSchema.INTENT);
        out.put("meanFieldAccuracy", Metrics.round(accSum / LabelSchema.FIELDS.size()));
        out.put("meanFieldMacroF1", Metrics.round(f1Sum / LabelSchema.FIELDS.size()));
        out.put("allFieldsCorrect", Metrics.rate(allCorrect, cls.size()));
        out.put("fields", fields);
        return out;
    }

    static Map<String, Object> extraction(List<PredictionRecord> q) {
        List<RecordEvaluation.ExtractionEval> ex = q.stream().filter(p -> p.evaluation() != null && p.evaluation().extraction() != null)
                .map(p -> p.evaluation().extraction()).toList();
        if (ex.isEmpty()) {
            return null;
        }
        long exact = ex.stream().filter(RecordEvaluation.ExtractionEval::exactMatch).count();
        long tp = ex.stream().mapToLong(RecordEvaluation.ExtractionEval::tp).sum();
        long fp = ex.stream().mapToLong(RecordEvaluation.ExtractionEval::fp).sum();
        long fn = ex.stream().mapToLong(RecordEvaluation.ExtractionEval::fn).sum();
        long predicted = ex.stream().mapToLong(RecordEvaluation.ExtractionEval::predictedTuples).sum();
        long halluc = ex.stream().mapToLong(e -> e.hallucinated().size()).sum();
        long missing = ex.stream().mapToLong(e -> e.missing().size()).sum();
        long goldTuples = tp + fn;
        double precision = tp + fp == 0 ? 0 : (double) tp / (tp + fp);
        double recall = tp + fn == 0 ? 0 : (double) tp / (tp + fn);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("n", ex.size());
        out.put("exactMatch", Metrics.rate(exact, ex.size()));
        out.put("exactMatchCi", Metrics.wilson(exact, ex.size()));
        out.put("entityPrecision", Metrics.round(precision));
        out.put("entityRecall", Metrics.round(recall));
        out.put("entityF1", Metrics.round(precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall)));
        out.put("hallucinationRate", predicted == 0 ? null : Metrics.round((double) halluc / predicted));
        out.put("hallucinatedValues", halluc);
        out.put("missingRate", goldTuples == 0 ? null : Metrics.round((double) missing / goldTuples));
        Map<String, Object> field = new LinkedHashMap<>();
        for (String f : List.of("orderId", "items", "total", "currency")) {
            field.put(f, Metrics.rate(ex.stream().filter(e -> Boolean.TRUE.equals(e.fieldCorrect().get(f))).count(), ex.size()));
        }
        out.put("fieldAccuracy", field);
        List<Boolean> offsets = ex.stream().map(RecordEvaluation.ExtractionEval::offsetCorrect).filter(Objects::nonNull).toList();
        out.put("offsetAccuracy", offsets.isEmpty() ? null : Metrics.rate(offsets.stream().filter(b -> b).count(), offsets.size()));
        out.put("offsetN", offsets.size());
        return out;
    }

    static Map<String, Object> decision(List<PredictionRecord> q) {
        List<RecordEvaluation.DecisionEval> d = q.stream().filter(p -> p.evaluation() != null && p.evaluation().decision() != null)
                .map(p -> p.evaluation().decision()).toList();
        if (d.isEmpty()) {
            return null;
        }
        long ok = d.stream().filter(RecordEvaluation.DecisionEval::correct).count();
        long falseAuto = d.stream().filter(x -> "auto_refund".equals(x.predicted()) && !"auto_refund".equals(x.gold())).count();
        return Map.of("n", d.size(), "accuracy", Metrics.rate(ok, d.size()), "accuracyCi", Metrics.wilson(ok, d.size()),
                "falseAutoRefunds", falseAuto);
    }

    static Map<String, Object> abstention(List<PredictionRecord> q) {
        List<PredictionRecord> cls = q.stream().filter(p -> p.evaluation() != null && p.evaluation().classification() != null).toList();
        if (cls.isEmpty()) {
            return null;
        }
        List<PredictionRecord> amb = cls.stream().filter(p -> "ambiguous".equals(p.group())).toList();
        long abst = amb.stream().filter(p -> LabelSchema.NONE.equals(p.evaluation().classification().get(LabelSchema.INTENT).predicted())).count();
        long fpos = amb.stream().filter(p -> p.outcome() == Outcome.SUCCESS
                && !LabelSchema.NONE.equals(p.evaluation().classification().get(LabelSchema.INTENT).predicted())).count();
        long covered = cls.stream().filter(p -> p.outcome() == Outcome.SUCCESS
                && !LabelSchema.NONE.equals(p.evaluation().classification().get(LabelSchema.INTENT).predicted())).count();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ambiguousN", amb.size());
        out.put("abstentionRate", Metrics.rate(abst, amb.size()));
        out.put("falsePositiveRate", Metrics.rate(fpos, amb.size()));
        out.put("coverage", Metrics.rate(covered, cls.size()));
        return out;
    }

    /** Pooled top-label calibration over all classification fields with native scores. */
    static Map<String, Object> calibration(List<PredictionRecord> q) {
        List<Metrics.ScoredOutcome> s = new ArrayList<>();
        long withoutScore = 0;
        long scoredFields = 0;
        for (PredictionRecord p : q) {
            if (p.evaluation() == null || p.evaluation().classification() == null) {
                continue;
            }
            for (RecordEvaluation.FieldEval fe : p.evaluation().classification().values()) {
                scoredFields++;
                if (fe.probability() != null) {
                    s.add(new Metrics.ScoredOutcome(fe.probability(), fe.correct()));
                } else {
                    withoutScore++;
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        if (s.isEmpty()) {
            out.put("available", false);
            out.put("reason", scoredFields == 0 ? "no classification predictions" : "provider returns no native label probabilities (none are invented)");
            return out;
        }
        Metrics.CalibrationResult r = Metrics.calibration(s);
        out.put("available", true);
        out.put("reason", null);
        out.put("definition", "top-label score of the chosen label (Kev: probabilities[choice]; GLiNER: label confidence), pooled over intent/sentiment/category/complaint; failed fields without a score are excluded here but counted in accuracy");
        out.put("fieldsWithoutScore", withoutScore);
        out.put("n", r.n());
        out.put("brier", r.brier());
        out.put("ece", r.ece());
        out.put("reliability", r.reliability());
        out.put("coverageCurve", r.coverageCurve());
        out.put("riskAtThreshold", Metrics.riskAt(s, REPORT_THRESHOLD));
        return out;
    }

    static Map<String, Object> latencyByTask(List<PredictionRecord> lat) {
        Map<String, Object> out = new TreeMap<>();
        for (TaskType t : TaskType.values()) {
            List<Double> v = lat.stream().filter(p -> p.task() == t).map(p -> p.timing().clientNanos() / 1e6).toList();
            if (!v.isEmpty()) {
                out.put(t.name(), Metrics.latency(v));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> throughput(Map<String, Object> run, List<PredictionRecord> all) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : (List<Map<String, Object>>) run.getOrDefault("throughput", List.of())) {
            int conc = ((Number) row.get("concurrency")).intValue();
            double wall = ((Number) row.get("wallSeconds")).doubleValue();
            int requests = ((Number) row.get("requests")).intValue();
            List<PredictionRecord> ok = all.stream().filter(p -> PredictionRecord.THROUGHPUT.equals(p.phase()) && p.concurrency() == conc
                    && p.outcome() == Outcome.SUCCESS).toList();
            List<Double> lat = ok.stream().map(p -> p.timing().clientNanos() / 1e6).toList();
            Metrics.LatencyStats ls = Metrics.latency(lat);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("concurrency", conc);
            r.put("requests", requests);
            r.put("wallSeconds", wall);
            r.put("rps", Metrics.round2((requests - ((Number) row.get("errors")).intValue()) / wall));
            r.put("p50", ls == null ? null : ls.p50());
            r.put("p95", ls == null ? null : ls.p95());
            r.put("errors", row.get("errors"));
            r.put("cacheFullHits", ok.stream().filter(p -> PromptCache.fullHit(p.rawResponse())).count());
            out.add(r);
        }
        return out;
    }

    static Map<String, Object> tokens(List<PredictionRecord> lat) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Integer> in = lat.stream().map(p -> p.timing().inputTokens()).filter(Objects::nonNull).toList();
        List<Integer> outT = lat.stream().map(p -> p.timing().outputTokens()).filter(Objects::nonNull).toList();
        out.put("meanInput", in.isEmpty() ? null : Metrics.round2(in.stream().mapToInt(Integer::intValue).average().orElse(0)));
        out.put("meanOutput", outT.isEmpty() ? null : Metrics.round2(outT.stream().mapToInt(Integer::intValue).average().orElse(0)));
        long genTokens = 0;
        long genNanos = 0;
        for (PredictionRecord p : lat) {
            if (p.timing().outputTokens() != null && p.timing().generationNanos() != null && p.timing().generationNanos() > 0) {
                genTokens += p.timing().outputTokens();
                genNanos += p.timing().generationNanos();
            }
        }
        out.put("outputTokensPerSecond", genNanos == 0 ? null : Metrics.round2(genTokens / (genNanos / 1e9)));
        out.put("tokensNote", "Ollama eval_count/eval_duration for generative models; Kev usage.output_tokens are internal scoring tokens, not generated text");
        return out;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> resources(Map<String, Object> run) {
        Map<String, Object> res = (Map<String, Object>) run.getOrDefault("resources", Map.of());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("processPattern", res.get("processPattern"));
        out.put("samples", res.getOrDefault("samples", 0));
        out.put("rssMeanMb", res.get("rssMeanMb"));
        out.put("rssPeakMb", res.get("rssPeakMb"));
        out.put("cpuMeanPct", res.get("cpuMeanPct"));
        out.put("cpuPeakPct", res.get("cpuPeakPct"));
        out.put("note", res.get("note"));
        out.put("ollamaLoaded", run.get("ollamaLoaded"));
        out.put("modelStorageBytes", run.get("modelStorageBytes"));
        out.put("gpu", "not measured (Apple GPU utilization requires root powermetrics; no NVIDIA GPU)");
        out.put("energy", "not measured (no reliable unprivileged energy measurement available)");
        return out;
    }

    static Map<String, Object> breakdown(List<PredictionRecord> q, java.util.function.Function<PredictionRecord, String> key) {
        Map<String, List<PredictionRecord>> by = new TreeMap<>();
        q.forEach(p -> by.computeIfAbsent(key.apply(p), k -> new ArrayList<>()).add(p));
        Map<String, Object> out = new LinkedHashMap<>();
        by.forEach((k, list) -> out.put(k, qualitySummary(list)));
        return out;
    }

    static Map<String, Object> qualitySummary(List<PredictionRecord> list) {
        Predicate<PredictionRecord> hasCls = p -> p.evaluation() != null && p.evaluation().classification() != null;
        Predicate<PredictionRecord> hasExt = p -> p.evaluation() != null && p.evaluation().extraction() != null;
        List<PredictionRecord> c = list.stream().filter(hasCls).toList();
        List<PredictionRecord> e = list.stream().filter(hasExt).toList();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("n", list.size());
        m.put("classificationAccuracy", c.isEmpty() ? null
                : Metrics.rate(c.stream().filter(p -> p.evaluation().classification().get(LabelSchema.INTENT).correct()).count(), c.size()));
        m.put("extractionExactMatch", e.isEmpty() ? null : Metrics.rate(e.stream().filter(p -> p.evaluation().extraction().exactMatch()).count(), e.size()));
        m.put("failures", list.stream().filter(p -> p.outcome() != Outcome.SUCCESS).count());
        return m;
    }

    static List<Map<String, Object>> perLength(List<PredictionRecord> q) {
        Map<String, List<PredictionRecord>> by = new TreeMap<>();
        for (PredictionRecord p : q) {
            if (p.recordMeta() != null && p.recordMeta().get("targetTokens") != null) {
                String k = String.format("%06d|%s", ((Number) p.recordMeta().get("targetTokens")).intValue(), p.recordMeta().get("position"));
                by.computeIfAbsent(k, x -> new ArrayList<>()).add(p);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        by.forEach((k, list) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("targetTokens", Integer.parseInt(k.split("\\|")[0]));
            m.put("position", k.split("\\|")[1]);
            m.put("n", list.size());
            List<PredictionRecord> e = list.stream().filter(p -> p.evaluation() != null && p.evaluation().extraction() != null).toList();
            m.put("exactMatch", e.isEmpty() ? null : Metrics.rate(e.stream().filter(p -> p.evaluation().extraction().exactMatch()).count(), e.size()));
            m.put("unsupported", list.stream().filter(p -> p.outcome() == Outcome.UNSUPPORTED).count());
            m.put("failures", list.stream().filter(p -> p.outcome() != Outcome.SUCCESS).count());
            Metrics.LatencyStats ls = Metrics.latency(list.stream().filter(p -> p.outcome() == Outcome.SUCCESS && !PromptCache.fullHit(p.rawResponse()))
                    .map(p -> p.timing().clientNanos() / 1e6).toList());
            m.put("latencyP50", ls == null ? null : ls.p50());
            m.put("meanInputTokens", list.stream().map(p -> p.timing().inputTokens()).filter(Objects::nonNull).mapToInt(Integer::intValue).average().orElse(Double.NaN));
            out.add(m);
        });
        out.forEach(m -> {
            if (m.get("meanInputTokens") instanceof Double d && d.isNaN()) {
                m.put("meanInputTokens", null);
            }
        });
        out.sort(Comparator.comparing((Map<String, Object> m) -> (Integer) m.get("targetTokens")));
        return out;
    }

    static Double fallbackRate(List<PredictionRecord> q) {
        List<PredictionRecord> withFlag = q.stream().filter(p -> p.metadata() != null && p.metadata().containsKey("fallbackUsed")).toList();
        if (withFlag.isEmpty()) {
            return null;
        }
        return Metrics.rate(withFlag.stream().filter(p -> Boolean.TRUE.equals(p.metadata().get("fallbackUsed"))).count(), withFlag.size());
    }
}

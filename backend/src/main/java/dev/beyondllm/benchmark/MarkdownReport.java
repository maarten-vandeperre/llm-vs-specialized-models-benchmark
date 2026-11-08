package dev.beyondllm.benchmark;

import dev.beyondllm.infrastructure.http.JsonHttp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Renders the run report as Markdown (results/runs/&lt;runId&gt;/report.md). Only values present in the report are printed. */
public final class MarkdownReport {

    private MarkdownReport() {
    }

    @SuppressWarnings("unchecked")
    public static String render(Map<String, Object> reportIn) {
        Map<String, Object> report = JsonHttp.MAPPER.convertValue(reportIn, Map.class);
        StringBuilder sb = new StringBuilder();
        Map<String, Object> env = (Map<String, Object>) report.getOrDefault("environment", Map.of());
        Map<String, Object> cfg = (Map<String, Object>) report.getOrDefault("config", Map.of());
        List<Map<String, Object>> models = (List<Map<String, Object>>) report.getOrDefault("models", List.of());

        sb.append("# Benchmark report ").append(report.get("runId")).append("\n\n");
        sb.append("> **Synthetic benchmark.** ").append(report.get("disclaimer")).append("\n\n");
        sb.append("- Status: ").append(report.get("status")).append(", created ").append(report.get("createdAt"))
                .append(", finished ").append(report.get("finishedAt")).append('\n');
        sb.append("- Dataset: `").append(cfg.get("dataset")).append("` split `").append(cfg.get("split")).append("`, groups ")
                .append(cfg.get("groups") == null ? "all" : cfg.get("groups")).append(", limit ").append(cfg.get("limit"))
                .append(", records selected ").append(env.get("records.selected")).append(", sha256 `").append(env.get("datasetSha256")).append("`\n");
        sb.append("- Method: warm-up ").append(cfg.get("warmup")).append(", repetitions ").append(cfg.get("repetitions"))
                .append(", latency sample ").append(cfg.get("latencySampleSize")).append(", concurrency ").append(cfg.get("concurrency"))
                .append(", throughput requests ").append(cfg.get("throughputRequests")).append(", cold start ").append(cfg.get("coldStart"))
                .append(", seed ").append(cfg.get("seed")).append(", timeout ").append(cfg.get("timeoutSeconds")).append(" s\n");
        sb.append("- Model order (seeded shuffle): ").append(env.get("modelOrder")).append('\n');
        sb.append("- Hardware: ").append(env.get("cpu.brand")).append(", ").append(gib(env.get("memory.bytes"))).append(" memory, ")
                .append(env.get("cpu.count")).append(" CPUs, ").append(env.get("os.name")).append(' ')
                .append(env.getOrDefault("macos.productVersion", env.get("os.version"))).append('\n');
        sb.append("- Software: Java ").append(env.get("java.version")).append(", Quarkus ").append(env.get("quarkus.version"))
                .append(", Ollama ").append(env.get("ollama.version")).append(", GLiNER service ").append(env.get("gliner.versions")).append('\n');
        sb.append("- Hybrid thresholds: ").append(env.get("hybridThresholds")).append("\n\n");
        List<String> errors = (List<String>) report.getOrDefault("errors", List.of());
        if (!errors.isEmpty()) {
            sb.append("## Skipped / errors\n\n");
            errors.forEach(e -> sb.append("- ").append(e).append('\n'));
            sb.append('\n');
        }

        List<String> warnings = new ArrayList<>();
        for (Map<String, Object> m : models) {
            Object rate = m.get("infraFailureRate");
            if (rate instanceof Number r && r.doubleValue() > 0.02) {
                warnings.add(m.get("modelId") + ": " + pct(r) + " of measured requests failed with INFRA_ERROR/TIMEOUT. "
                        + "Latency and throughput for this model are computed on successful requests only and may be unrepresentative.");
            }
        }
        if (!warnings.isEmpty()) {
            sb.append("## ⚠ Warnings\n\n");
            warnings.forEach(w -> sb.append("- ").append(w).append('\n'));
            sb.append('\n');
        }

        sb.append("## Quality (quality phase, failures counted as errors)\n\n");
        sb.append("| Model | n | Intent acc [95% CI] | Intent macro-F1 [CI] | Mean field acc | Extraction EM [CI] | Entity F1 | Halluc. rate | Missing rate | Decision acc | Failures (unsup/malf/timeout/infra) |\n");
        sb.append("|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (Map<String, Object> m : models) {
            Map<String, Object> c = (Map<String, Object>) m.get("classification");
            Map<String, Object> e = (Map<String, Object>) m.get("extraction");
            Map<String, Object> d = (Map<String, Object>) m.get("decision");
            Map<String, Object> n = (Map<String, Object>) m.getOrDefault("counts", Map.of());
            sb.append("| ").append(m.get("modelId")).append(" | ").append(n.get("total"))
                    .append(" | ").append(c == null ? "–" : pct(c.get("accuracy")) + " " + ci(c.get("accuracyCi")))
                    .append(" | ").append(c == null ? "–" : num(c.get("macroF1")) + " " + ci(c.get("macroF1Ci"), false))
                    .append(" | ").append(c == null ? "–" : pct(c.get("meanFieldAccuracy")))
                    .append(" | ").append(e == null ? "–" : pct(e.get("exactMatch")) + " " + ci(e.get("exactMatchCi")))
                    .append(" | ").append(e == null ? "–" : num(e.get("entityF1")))
                    .append(" | ").append(e == null ? "–" : pct(e.get("hallucinationRate")))
                    .append(" | ").append(e == null ? "–" : pct(e.get("missingRate")))
                    .append(" | ").append(d == null ? "–" : pct(d.get("accuracy")))
                    .append(" | ").append(n.get("unsupported")).append('/').append(n.get("malformed")).append('/').append(n.get("timeout")).append('/').append(n.get("infraError"))
                    .append(" |\n");
            if (m.get("skipped") != null) {
                sb.append("| ↳ ").append(m.get("modelId")).append(" skipped: ").append(m.get("skipped")).append(" |||||||||||\n");
            }
        }
        sb.append('\n');

        sb.append("## Per-field classification accuracy\n\n| Model | intent | sentiment | category | complaint | all four correct |\n|---|---|---|---|---|---|\n");
        for (Map<String, Object> m : models) {
            Map<String, Object> c = (Map<String, Object>) m.get("classification");
            if (c == null) {
                continue;
            }
            Map<String, Object> f = (Map<String, Object>) c.get("fields");
            sb.append("| ").append(m.get("modelId"));
            for (String k : List.of("intent", "sentiment", "category", "complaint")) {
                sb.append(" | ").append(pct(((Map<String, Object>) f.get(k)).get("accuracy")));
            }
            sb.append(" | ").append(pct(c.get("allFieldsCorrect"))).append(" |\n");
        }
        sb.append('\n');

        sb.append("## Extraction field accuracy\n\n| Model | orderId | items | total | currency | offset acc (n) |\n|---|---|---|---|---|---|\n");
        for (Map<String, Object> m : models) {
            Map<String, Object> e = (Map<String, Object>) m.get("extraction");
            if (e == null) {
                continue;
            }
            Map<String, Object> f = (Map<String, Object>) e.get("fieldAccuracy");
            sb.append("| ").append(m.get("modelId")).append(" | ").append(pct(f.get("orderId"))).append(" | ").append(pct(f.get("items")))
                    .append(" | ").append(pct(f.get("total"))).append(" | ").append(pct(f.get("currency"))).append(" | ")
                    .append(e.get("offsetAccuracy") == null ? "n/a" : pct(e.get("offsetAccuracy")) + " (" + e.get("offsetN") + ")").append(" |\n");
        }
        sb.append('\n');

        sb.append("## Latency (client-side, concurrency 1)\n\n")
                .append("Headline columns: each evaluation input sent once (quality phase), successful requests, full prompt-cache hits excluded. ")
                .append("\"repeat p50\" is the latency-phase median over repeated identical inputs, which a runtime may serve from its prompt cache.\n\n")
                .append("| Model | n | p50 ms [CI] | p90 | p95 | p99 | server p50 ms | cold start ms (load ms) | out tok/s | repeat p50 ms | cache full hits (first-seen / repeat) |\n|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (Map<String, Object> m : models) {
            Map<String, Object> l = (Map<String, Object>) m.get("latency");
            Map<String, Object> s = (Map<String, Object>) m.get("serverLatency");
            Map<String, Object> cs = (Map<String, Object>) m.get("coldStart");
            Map<String, Object> t = (Map<String, Object>) m.getOrDefault("tokens", Map.of());
            Map<String, Object> rl = (Map<String, Object>) m.get("repeatLatency");
            Map<String, Object> pc = (Map<String, Object>) m.getOrDefault("promptCache", Map.of());
            if (l == null) {
                sb.append("| ").append(m.get("modelId")).append(" | 0 | – | – | – | – | – | – | – | – | – |\n");
                continue;
            }
            sb.append("| ").append(m.get("modelId")).append(" | ").append(l.get("n")).append(" | ").append(num(l.get("p50"))).append(' ').append(ci(l.get("p50Ci"), false))
                    .append(" | ").append(num(l.get("p90"))).append(" | ").append(num(l.get("p95"))).append(" | ")
                    .append(l.get("p99") == null ? "n<100" : num(l.get("p99"))).append(" | ").append(s == null ? "n/a" : num(s.get("p50")))
                    .append(" | ").append(cs == null ? "n/a" : num(cs.get("clientMs")) + " (" + (cs.get("loadMs") == null ? "n/a" : num(cs.get("loadMs"))) + ")")
                    .append(" | ").append(t.get("outputTokensPerSecond") == null ? "n/a" : num(t.get("outputTokensPerSecond")))
                    .append(" | ").append(rl == null ? "n/a" : num(rl.get("p50")))
                    .append(" | ").append(pc.get("qualityFullHits") == null ? "n/a" : pc.get("qualityFullHits") + " / " + pc.get("repeatFullHits") + " of " + pc.get("repeatRequests"))
                    .append(" |\n");
        }
        sb.append('\n');

        sb.append("## Throughput\n\nEach concurrency level uses its own window of inputs not used by the warm-up or latency phases.\n\n")
                .append("| Model | concurrency | requests | wall s | req/s | p50 ms | p95 ms | errors | cache full hits |\n|---|---|---|---|---|---|---|---|---|\n");
        for (Map<String, Object> m : models) {
            for (Map<String, Object> r : (List<Map<String, Object>>) m.getOrDefault("throughput", List.of())) {
                sb.append("| ").append(m.get("modelId")).append(" | ").append(r.get("concurrency")).append(" | ").append(r.get("requests"))
                        .append(" | ").append(num(r.get("wallSeconds"))).append(" | ").append(num(r.get("rps"))).append(" | ").append(num(r.get("p50")))
                        .append(" | ").append(num(r.get("p95"))).append(" | ").append(r.get("errors"))
                        .append(" | ").append(r.get("cacheFullHits") == null ? "n/a" : r.get("cacheFullHits")).append(" |\n");
            }
        }
        sb.append('\n');

        sb.append("## Confidence calibration (native scores only)\n\n| Model | n | Brier | ECE | risk @0.80 (coverage) | note |\n|---|---|---|---|---|---|\n");
        for (Map<String, Object> m : models) {
            Map<String, Object> c = (Map<String, Object>) m.getOrDefault("calibration", Map.of());
            if (Boolean.TRUE.equals(c.get("available"))) {
                Map<String, Object> r = (Map<String, Object>) c.get("riskAtThreshold");
                sb.append("| ").append(m.get("modelId")).append(" | ").append(c.get("n")).append(" | ").append(num(c.get("brier"))).append(" | ")
                        .append(num(c.get("ece"))).append(" | ").append(r == null ? "n/a" : pct(r.get("risk")) + " (" + pct(r.get("coverage")) + ")")
                        .append(" | top-label score |\n");
            } else {
                sb.append("| ").append(m.get("modelId")).append(" | – | – | – | – | ").append(c.get("reason")).append(" |\n");
            }
        }
        sb.append('\n');

        sb.append("## Abstention on ambiguous inputs\n\n| Model | n ambiguous | predicted `none` | false-positive decisions | overall coverage |\n|---|---|---|---|---|\n");
        for (Map<String, Object> m : models) {
            Map<String, Object> a = (Map<String, Object>) m.get("abstention");
            if (a == null || ((Number) a.get("ambiguousN")).intValue() == 0) {
                continue;
            }
            sb.append("| ").append(m.get("modelId")).append(" | ").append(a.get("ambiguousN")).append(" | ").append(pct(a.get("abstentionRate")))
                    .append(" | ").append(pct(a.get("falsePositiveRate"))).append(" | ").append(pct(a.get("coverage"))).append(" |\n");
        }
        sb.append('\n');

        sb.append("## Per language (intent accuracy / extraction exact match)\n\n| Model | en | nl | fr |\n|---|---|---|---|\n");
        for (Map<String, Object> m : models) {
            Map<String, Object> pl = (Map<String, Object>) m.getOrDefault("perLanguage", Map.of());
            sb.append("| ").append(m.get("modelId"));
            for (String lang : List.of("en", "nl", "fr")) {
                Map<String, Object> x = (Map<String, Object>) pl.get(lang);
                sb.append(" | ").append(x == null ? "–" : pct(x.get("classificationAccuracy")) + " / " + pct(x.get("extractionExactMatch")) + " (n=" + x.get("n") + ")");
            }
            sb.append(" |\n");
        }
        sb.append('\n');

        sb.append("## Input length (long group, extraction exact match / latency p50 ms / unsupported)\n\n| Model | tokens | position | n | EM | p50 ms | unsupported |\n|---|---|---|---|---|---|---|\n");
        for (Map<String, Object> m : models) {
            for (Map<String, Object> r : (List<Map<String, Object>>) m.getOrDefault("perLength", List.of())) {
                sb.append("| ").append(m.get("modelId")).append(" | ").append(r.get("targetTokens")).append(" | ").append(r.get("position"))
                        .append(" | ").append(r.get("n")).append(" | ").append(pct(r.get("exactMatch"))).append(" | ").append(num(r.get("latencyP50")))
                        .append(" | ").append(r.get("unsupported")).append(" |\n");
            }
        }
        sb.append('\n');

        sb.append("## Resources (sampled during this model's phases)\n\n| Model | RSS mean MB | RSS peak MB | CPU mean % | CPU peak % | Ollama loaded size | storage | note |\n|---|---|---|---|---|---|---|---|\n");
        for (Map<String, Object> m : models) {
            Map<String, Object> r = (Map<String, Object>) m.getOrDefault("resources", Map.of());
            sb.append("| ").append(m.get("modelId")).append(" | ").append(num(r.get("rssMeanMb"))).append(" | ").append(num(r.get("rssPeakMb")))
                    .append(" | ").append(num(r.get("cpuMeanPct"))).append(" | ").append(num(r.get("cpuPeakPct"))).append(" | ")
                    .append(loaded(r.get("ollamaLoaded"))).append(" | ").append(gib(r.get("modelStorageBytes"))).append(" | ")
                    .append(r.get("note") == null ? "" : r.get("note")).append(" |\n");
        }
        sb.append("\nGPU utilization and energy: not measured (no reliable unprivileged method on this platform).\n");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static String loaded(Object o) {
        if (!(o instanceof List<?> l) || l.isEmpty()) {
            return "n/a";
        }
        StringBuilder sb = new StringBuilder();
        for (Object x : l) {
            Map<String, Object> m = (Map<String, Object>) x;
            sb.append(m.get("name")).append(": ").append(gib(m.get("sizeBytes"))).append(' ');
        }
        return sb.toString().trim();
    }

    static String pct(Object o) {
        return o instanceof Number n ? String.format(Locale.ROOT, "%.1f%%", n.doubleValue() * 100) : "n/a";
    }

    static String num(Object o) {
        return o instanceof Number n ? String.format(Locale.ROOT, "%.2f", n.doubleValue()) : "n/a";
    }

    static String gib(Object o) {
        return o instanceof Number n ? String.format(Locale.ROOT, "%.2f GiB", n.doubleValue() / (1L << 30)) : "n/a";
    }

    static String ci(Object o) {
        return ci(o, true);
    }

    static String ci(Object o, boolean percent) {
        if (o instanceof List<?> l && l.size() == 2) {
            return percent ? "[" + pct(l.get(0)) + "–" + pct(l.get(1)) + "]" : "[" + num(l.get(0)) + "–" + num(l.get(1)) + "]";
        }
        if (o instanceof double[] d && d.length == 2) {
            return percent ? "[" + pct(d[0]) + "–" + pct(d[1]) + "]" : "[" + num(d[0]) + "–" + num(d[1]) + "]";
        }
        return "";
    }
}

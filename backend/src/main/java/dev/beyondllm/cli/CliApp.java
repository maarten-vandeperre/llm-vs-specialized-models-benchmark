package dev.beyondllm.cli;

import dev.beyondllm.application.BenchmarkModel;
import dev.beyondllm.application.ModelRegistry;
import dev.beyondllm.benchmark.BenchmarkConfig;
import dev.beyondllm.benchmark.BenchmarkRunner;
import dev.beyondllm.benchmark.RunStatus;
import dev.beyondllm.dataset.DatasetGenerator;
import dev.beyondllm.dataset.DatasetRecord;
import dev.beyondllm.dataset.DatasetRepository;
import dev.beyondllm.domain.BenchmarkRequest;
import dev.beyondllm.domain.BenchmarkResponse;
import dev.beyondllm.domain.HealthStatus;
import dev.beyondllm.domain.LabelPrediction;
import dev.beyondllm.domain.LabelSchema;
import dev.beyondllm.domain.TaskType;
import dev.beyondllm.evaluation.Metrics;
import dev.beyondllm.infrastructure.http.JsonHttp;
import dev.beyondllm.infrastructure.hybrid.HybridThresholds;
import dev.beyondllm.infrastructure.persistence.ResultStore;
import dev.beyondllm.infrastructure.system.EnvironmentInspector;
import io.quarkus.runtime.QuarkusApplication;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Headless commands (see {@link Main}). */
public class CliApp implements QuarkusApplication {

    @Inject
    ModelRegistry registry;
    @Inject
    BenchmarkRunner runner;
    @Inject
    DatasetRepository datasets;
    @Inject
    ResultStore store;
    @Inject
    EnvironmentInspector environment;
    @Inject
    HybridThresholds thresholds;
    @ConfigProperty(name = "bench.datasets-dir", defaultValue = "../datasets")
    String datasetsDir;
    @ConfigProperty(name = "bench.results-dir", defaultValue = "../results")
    String resultsDir;

    @Override
    public int run(String... args) throws Exception {
        Map<String, String> opt = parse(Arrays.copyOfRange(args, 1, args.length));
        return switch (args[0]) {
            case "generate-dataset" -> generate(opt);
            case "benchmark" -> benchmark(opt);
            case "calibrate" -> calibrate(opt);
            case "health" -> health();
            case "env" -> {
                System.out.println(JsonHttp.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(environment.capture()));
                yield 0;
            }
            case "report" -> {
                if (args.length < 2 || runner.status(args[1]).isEmpty()) {
                    System.err.println("Usage: report <runId>. Unknown run in " + store.runDir("x").getParent().toAbsolutePath().normalize());
                    yield 2;
                }
                runner.regenerateReport(args[1]);
                System.out.println(store.runDir(args[1]).resolve("report.md").toAbsolutePath().normalize());
                yield 0;
            }
            default -> {
                System.err.println("Unknown command " + args[0] + ". Commands: serve, generate-dataset, benchmark, calibrate, health, env, report <runId>");
                yield 2;
            }
        };
    }

    static Map<String, String> parse(String[] args) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String a : args) {
            if (a.startsWith("--")) {
                int eq = a.indexOf('=');
                if (eq > 0) {
                    m.put(a.substring(2, eq), a.substring(eq + 1));
                } else {
                    m.put(a.substring(2), "true");
                }
            } else {
                m.put("_", a);
            }
        }
        return m;
    }

    private static List<String> list(String v) {
        return v == null || v.isBlank() ? null : Arrays.stream(v.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    int generate(Map<String, String> opt) {
        long seed = Long.parseLong(opt.getOrDefault("seed", String.valueOf(DatasetGenerator.DEFAULT_SEED)));
        Path out = opt.containsKey("out") ? Path.of(opt.get("out")) : DatasetRepository.coffeeFile(dev.beyondllm.infrastructure.persistence.ProjectPaths.resolve(datasetsDir));
        List<DatasetRecord> recs = new DatasetGenerator(seed).generate();
        DatasetRepository.write(recs, out);
        Map<String, Long> groups = new LinkedHashMap<>();
        recs.forEach(r -> groups.merge(r.group() + "/" + r.split(), 1L, Long::sum));
        System.out.printf("Wrote %d records (seed %d) to %s%n%s%n", recs.size(), seed, out.toAbsolutePath().normalize(), groups);
        return 0;
    }

    int health() {
        int down = 0;
        for (BenchmarkModel m : registry.all()) {
            HealthStatus h = m.health();
            System.out.printf("%-26s %-20s %s%n", m.id(), h.state(), h.detail());
            if (!h.isUp()) {
                down++;
            }
        }
        return down == registry.all().size() ? 1 : 0;
    }

    int benchmark(Map<String, String> opt) {
        List<String> models = list(opt.get("models"));
        if (models == null) {
            models = registry.all().stream().filter(m -> !m.requiresExplicitSelection())
                    .filter(m -> m.health().isUp()).map(BenchmarkModel::id).toList();
            System.out.println("No --models given; using all healthy local candidates (commercial APIs need --models): " + models);
        }
        BenchmarkConfig base = "smoke".equals(opt.get("profile")) ? BenchmarkConfig.smoke(models) : BenchmarkConfig.defaults(models);
        BenchmarkConfig c = new BenchmarkConfig(models,
                opt.getOrDefault("dataset", base.dataset()),
                opt.containsKey("groups") ? list(opt.get("groups")) : base.groups(),
                opt.getOrDefault("split", base.split()),
                opt.containsKey("languages") ? list(opt.get("languages")) : base.languages(),
                opt.containsKey("limit") ? Integer.valueOf(opt.get("limit")) : base.limit(),
                Integer.parseInt(opt.getOrDefault("warmup", String.valueOf(base.warmup()))),
                Integer.parseInt(opt.getOrDefault("repetitions", String.valueOf(base.repetitions()))),
                Integer.parseInt(opt.getOrDefault("latency-sample", String.valueOf(base.latencySampleSize()))),
                opt.containsKey("concurrency") ? list(opt.get("concurrency")).stream().map(Integer::valueOf).toList() : base.concurrency(),
                Integer.parseInt(opt.getOrDefault("throughput-requests", String.valueOf(base.throughputRequests()))),
                Integer.parseInt(opt.getOrDefault("timeout", String.valueOf(base.timeoutSeconds()))),
                Boolean.parseBoolean(opt.getOrDefault("cold-start", String.valueOf(base.coldStart()))),
                Long.parseLong(opt.getOrDefault("seed", String.valueOf(base.seed()))),
                opt.getOrDefault("label", base.label()));
        System.out.println("Running benchmark: " + c);
        RunStatus s = runner.runSync(c);
        System.out.println("Run " + s.runId() + " finished with status " + s.status());
        s.errors().forEach(e -> System.out.println("  ! " + e));
        System.out.println("Report: " + store.runDir(s.runId()).resolve("report.md").toAbsolutePath().normalize());
        return s.status() == RunStatus.State.COMPLETED ? 0 : 1;
    }

    /**
     * Derives hybrid routing thresholds from the CALIBRATION split only: the lowest score threshold at which the
     * accepted (non-routed) predictions reach the target selective accuracy for intent+complaint.
     */
    int calibrate(Map<String, String> opt) throws Exception {
        double target = Double.parseDouble(opt.getOrDefault("target-accuracy", "0.95"));
        Integer limit = opt.containsKey("limit") ? Integer.valueOf(opt.get("limit")) : null;
        List<DatasetRecord> recs = datasets.select(DatasetGenerator.DATASET_NAME,
                new DatasetRepository.Filter(List.of("classification", "combined", "ambiguous", "multilingual"), "calibration", null, limit));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("split", "calibration");
        out.put("records", recs.size());
        out.put("targetSelectiveAccuracy", target);
        out.put("definition", "score = min(p(intent), p(complaint)); correct = both intent and complaint correct; failures count as incorrect with score 0");
        Path dir = dev.beyondllm.infrastructure.persistence.ProjectPaths.resolve(resultsDir).resolve("calibration");
        Files.createDirectories(dir);
        Map<String, String> map = Map.of("kev", opt.getOrDefault("kev-model", "kev-0.8b"), "gliner", opt.getOrDefault("gliner-model", "gliner2.5-base"));
        for (Map.Entry<String, String> e : map.entrySet()) {
            BenchmarkModel m = registry.require(e.getValue());
            if (!m.health().isUp()) {
                System.out.println("Skipping " + m.id() + ": " + m.health().detail());
                continue;
            }
            List<Metrics.ScoredOutcome> scored = new ArrayList<>();
            StringBuilder lines = new StringBuilder();
            for (DatasetRecord r : recs) {
                BenchmarkResponse resp = m.execute(new BenchmarkRequest(r.id(), TaskType.CLASSIFICATION, r.text(), r.language()));
                double score = 0;
                boolean correct = false;
                if (resp.isSuccess()) {
                    LabelPrediction i = resp.prediction().labels().get(LabelSchema.INTENT);
                    LabelPrediction c = resp.prediction().labels().get(LabelSchema.COMPLAINT);
                    score = Math.min(i.probability() == null ? 0 : i.probability(), c.probability() == null ? 0 : c.probability());
                    correct = r.gold().intent().equals(i.label()) && r.gold().complaint().equals(c.label());
                }
                scored.add(new Metrics.ScoredOutcome(score, correct));
                lines.append(JsonHttp.MAPPER.writeValueAsString(Map.of("recordId", r.id(), "score", score, "correct", correct,
                        "outcome", resp.outcome()))).append('\n');
            }
            Files.writeString(dir.resolve(m.id() + "-calibration.jsonl"), lines.toString(), StandardCharsets.UTF_8);
            double t = Metrics.thresholdForTargetAccuracy(scored, target, 10);
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("model", m.id());
            d.put("threshold", t);
            d.put("riskAtThreshold", Metrics.riskAt(scored, t));
            d.put("calibration", Metrics.calibration(scored));
            out.put(e.getKey(), t);
            out.put(e.getKey() + "Details", d);
            System.out.printf("%s: threshold %.4f → %s%n", m.id(), t, Metrics.riskAt(scored, t));
        }
        JsonHttp.MAPPER.writerWithDefaultPrettyPrinter().writeValue(dir.resolve("thresholds.json").toFile(), out);
        System.out.println("Wrote " + dir.resolve("thresholds.json").toAbsolutePath().normalize());
        return 0;
    }
}

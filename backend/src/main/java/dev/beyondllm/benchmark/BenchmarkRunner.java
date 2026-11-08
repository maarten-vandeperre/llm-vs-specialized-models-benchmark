package dev.beyondllm.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import dev.beyondllm.application.BenchmarkModel;
import dev.beyondllm.application.ModelRegistry;
import dev.beyondllm.dataset.DatasetRecord;
import dev.beyondllm.dataset.DatasetRepository;
import dev.beyondllm.domain.BenchmarkRequest;
import dev.beyondllm.domain.BenchmarkResponse;
import dev.beyondllm.domain.HealthStatus;
import dev.beyondllm.domain.ModelCapabilities;
import dev.beyondllm.domain.TaskType;
import dev.beyondllm.evaluation.Evaluator;
import dev.beyondllm.infrastructure.hybrid.HybridThresholds;
import dev.beyondllm.infrastructure.ollama.OllamaAdminClient;
import dev.beyondllm.infrastructure.persistence.ResultStore;
import dev.beyondllm.infrastructure.system.EnvironmentInspector;
import dev.beyondllm.infrastructure.system.ResourceSampler;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Executes benchmark runs. Per candidate (in seeded-shuffled order): contention control (unload unrelated Ollama
 * models) → optional cold start → warm-up (not scored) → quality pass (every compatible record once, concurrency 1)
 * → latency repetitions → throughput at each concurrency level. Every attempt, including failures, is persisted.
 */
@ApplicationScoped
public class BenchmarkRunner {

    private static final Logger LOG = Logger.getLogger(BenchmarkRunner.class);

    private final ModelRegistry registry;
    private final DatasetRepository datasets;
    private final ResultStore store;
    private final EnvironmentInspector environment;
    private final OllamaAdminClient ollama;
    private final HybridThresholds thresholds;
    private final Map<String, RunStatus> live = new ConcurrentHashMap<>();
    private final ExecutorService background = Executors.newSingleThreadExecutor(r -> new Thread(r, "benchmark-run"));

    public BenchmarkRunner(ModelRegistry registry, DatasetRepository datasets, ResultStore store, EnvironmentInspector environment,
                           OllamaAdminClient ollama, HybridThresholds thresholds) {
        this.registry = registry;
        this.datasets = datasets;
        this.store = store;
        this.environment = environment;
        this.ollama = ollama;
        this.thresholds = thresholds;
    }

    /** A request task a candidate can execute for a record, or empty if incompatible. */
    public static Optional<TaskType> requestTask(ModelCapabilities caps, TaskType recordTask) {
        if (caps.supports(recordTask)) {
            return Optional.of(recordTask);
        }
        if (recordTask == TaskType.COMBINED && caps.supports(TaskType.CLASSIFICATION)) {
            return Optional.of(TaskType.CLASSIFICATION);
        }
        if (recordTask == TaskType.COMBINED && caps.supports(TaskType.EXTRACTION)) {
            return Optional.of(TaskType.EXTRACTION);
        }
        return Optional.empty();
    }

    public RunStatus submit(BenchmarkConfig config) {
        RunStatus s = newRun(config);
        background.submit(() -> execute(s.runId(), config));
        return s;
    }

    public RunStatus runSync(BenchmarkConfig config) {
        RunStatus s = newRun(config);
        execute(s.runId(), config);
        return status(s.runId()).orElseThrow();
    }

    public Optional<RunStatus> status(String runId) {
        RunStatus s = live.get(runId);
        return s != null ? Optional.of(s) : store.readJson(runId, "status.json", RunStatus.class);
    }

    public List<RunStatus> list() {
        Map<String, RunStatus> all = new LinkedHashMap<>();
        live.values().forEach(s -> all.put(s.runId(), s));
        store.listStatuses().forEach(s -> all.putIfAbsent(s.runId(), s));
        List<RunStatus> l = new ArrayList<>(all.values());
        l.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));
        return l;
    }

    private RunStatus newRun(BenchmarkConfig config) {
        for (String m : config.models()) {
            registry.require(m);
        }
        datasets.load(config.dataset());
        String id = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now())
                + "-" + Integer.toHexString(new Random().nextInt(0xffff) | 0x1000);
        RunStatus s = new RunStatus(id, config.label(), RunStatus.State.QUEUED, new RunStatus.Progress(0, 0, "queued", null),
                List.of(), config, Instant.now(), null, null);
        update(s);
        return s;
    }

    private void update(RunStatus s) {
        live.put(s.runId(), s);
        store.writeJson(s.runId(), "status.json", s);
    }

    private final class Ctx {
        final String runId;
        final BenchmarkConfig config;
        final AtomicLong done = new AtomicLong();
        final long total;
        final List<String> errors = new CopyOnWriteArrayList<>();
        final Instant started = Instant.now();
        volatile String phase = "starting";
        volatile String model;

        Ctx(String runId, BenchmarkConfig config, long total) {
            this.runId = runId;
            this.config = config;
            this.total = total;
        }

        void publish(RunStatus.State state, Instant finished) {
            RunStatus prev = live.get(runId);
            update(new RunStatus(runId, config.label(), state, new RunStatus.Progress(done.get(), total, phase, model), List.copyOf(errors),
                    config, prev == null ? started : prev.createdAt(), started, finished));
        }
    }

    private final Map<String, List<Item>> warmupItems = new java.util.concurrent.ConcurrentHashMap<>();

    record Item(DatasetRecord record, TaskType task) {
    }

    void execute(String runId, BenchmarkConfig config) {
        Ctx ctx = null;
        try {
            List<DatasetRecord> records = datasets.select(config.dataset(),
                    new DatasetRepository.Filter(config.groups(), config.split(), config.languages(), config.limit()));
            List<BenchmarkModel> models = new ArrayList<>(config.models().stream().map(registry::require).toList());
            Collections.shuffle(models, new Random(config.seed()));
            Map<String, List<Item>> items = new LinkedHashMap<>();
            // Warm-up pool: records of the same dataset that are NOT in the evaluated selection (e.g. the calibration
            // split), so a runtime's prompt cache cannot serve a later measured request from a warm-up request.
            java.util.Set<String> selectedIds = new java.util.HashSet<>();
            records.forEach(r -> selectedIds.add(r.id()));
            List<DatasetRecord> warmPool = new ArrayList<>(datasets.load(config.dataset()).stream()
                    .filter(r -> !selectedIds.contains(r.id())).toList());
            Collections.shuffle(warmPool, new Random(config.seed()));
            long total = 0;
            for (BenchmarkModel m : models) {
                List<Item> its = new ArrayList<>();
                for (DatasetRecord r : records) {
                    requestTask(m.capabilities(), r.task()).ifPresent(t -> its.add(new Item(r, t)));
                }
                items.put(m.id(), its);
                List<Item> warm = new ArrayList<>();
                for (DatasetRecord r : warmPool) {
                    if (warm.size() >= config.warmup()) {
                        break;
                    }
                    requestTask(m.capabilities(), r.task()).ifPresent(t -> warm.add(new Item(r, t)));
                }
                warmupItems.put(m.id(), warm);
                if (!its.isEmpty()) {
                    total += (config.coldStart() ? 1 : 0) + config.warmup() + its.size()
                            + (long) (config.repetitions() - 1) * Math.min(config.latencySampleSize(), its.size())
                            + (long) config.concurrency().size() * config.throughputRequests();
                }
            }
            ctx = new Ctx(runId, config, total);
            ctx.publish(RunStatus.State.RUNNING, null);

            Map<String, Object> env = environment.capture();
            env.put("modelOrder", models.stream().map(BenchmarkModel::id).toList());
            env.put("records.selected", records.size());
            env.put("datasetSha256", datasets.describe(config.dataset()).map(DatasetRepository.DatasetDescriptor::sha256).orElse(null));
            env.put("hybridThresholds", thresholds.describe());
            store.writeJson(runId, "environment.json", env);

            Map<String, Object> modelRuns = new LinkedHashMap<>();
            for (BenchmarkModel m : models) {
                ctx.model = m.id();
                Map<String, Object> mr = new LinkedHashMap<>();
                modelRuns.put(m.id(), mr);
                HealthStatus h = m.health();
                mr.put("health", h);
                mr.put("info", m.info());
                mr.put("capabilities", m.capabilities());
                List<Item> its = items.get(m.id());
                mr.put("compatibleRecords", its.size());
                mr.put("incompatibleRecords", records.size() - its.size());
                if (!h.isUp()) {
                    ctx.errors.add(m.id() + ": skipped – " + h.state() + ": " + h.detail());
                    mr.put("skipped", h.state() + ": " + h.detail());
                    continue;
                }
                if (its.isEmpty()) {
                    mr.put("skipped", "no compatible records in selection");
                    continue;
                }
                runModel(ctx, m, its, mr);
                store.writeJson(runId, "model-runs.json", modelRuns);
            }
            store.writeJson(runId, "model-runs.json", modelRuns);
            ctx.phase = "reporting";
            ctx.model = null;
            ctx.publish(RunStatus.State.RUNNING, null);
            List<PredictionRecord> preds = store.predictions(runId);
            store.writeCsv(runId, preds);
            ctx.phase = "done";
            ctx.publish(RunStatus.State.COMPLETED, Instant.now());
            Map<String, Object> report = ReportBuilder.build(status(runId).orElseThrow(), env, modelRuns, preds);
            store.writeJson(runId, "report.json", report);
            store.writeText(runId, "report.md", MarkdownReport.render(report));
        } catch (Exception e) {
            LOG.error("Run " + runId + " failed", e);
            if (ctx == null) {
                ctx = new Ctx(runId, config, 0);
            }
            ctx.errors.add("Run failed: " + e);
            ctx.phase = "failed";
            ctx.publish(RunStatus.State.FAILED, Instant.now());
        }
    }

    private void runModel(Ctx ctx, BenchmarkModel m, List<Item> its, Map<String, Object> mr) throws Exception {
        BenchmarkConfig c = ctx.config;
        // contention control: only the Ollama models this candidate needs stay loaded
        List<String> needed = m.runtimeModels();
        ollama.unloadAllExcept(needed);
        mr.put("concurrentlyLoadedRuntimeModels", needed);

        try (ResourceSampler sampler = new ResourceSampler(m.info().processPattern(), 500)) {
            if (c.coldStart()) {
                ctx.phase = "cold";
                boolean unloaded = m.unload();
                PredictionRecord r = call(ctx, m, its.getFirst(), PredictionRecord.COLD, 1, 1);
                Map<String, Object> cold = new LinkedHashMap<>();
                cold.put("unloadedBeforeRequest", unloaded);
                cold.put("clientMs", r.timing().clientMillis());
                cold.put("loadMs", r.timing().loadNanos() == null ? null : r.timing().loadNanos() / 1e6);
                cold.put("outcome", r.outcome());
                if (!unloaded) {
                    cold.put("note", "runtime does not support unloading from the client; first request after start-up is not a guaranteed cold start");
                }
                mr.put("coldStart", cold);
            }
            ctx.phase = "warmup";
            // Warm-up uses records outside the evaluated selection; only if there are none (the whole dataset is
            // selected) does it fall back to the end of the list.
            List<Item> warm = warmupItems.getOrDefault(m.id(), List.of());
            for (int i = 0; i < c.warmup(); i++) {
                Item w = warm.isEmpty() ? its.get(Math.floorMod(its.size() - 1 - i, its.size())) : warm.get(i % warm.size());
                call(ctx, m, w, PredictionRecord.WARMUP, 0, 1);
            }
            ctx.phase = "quality";
            for (Item it : its) {
                call(ctx, m, it, PredictionRecord.QUALITY, 1, 1);
            }
            ctx.phase = "latency";
            int sample = Math.min(c.latencySampleSize(), its.size());
            for (int rep = 2; rep <= c.repetitions(); rep++) {
                for (int i = 0; i < sample; i++) {
                    call(ctx, m, its.get(i), PredictionRecord.LATENCY, rep, 1);
                }
            }
            List<Map<String, Object>> tp = new ArrayList<>();
            if (c.throughputRequests() > 0) {
                int window = 0;
                for (int conc : c.concurrency()) {
                    ctx.phase = "throughput c=" + conc;
                    // Fresh inputs per level: skip the latency sample and the previous levels' windows, so repeated
                    // identical prompts (cache hits) do not inflate throughput.
                    final int offset = sample + window * c.throughputRequests();
                    window++;
                    ExecutorService pool = Executors.newFixedThreadPool(conc);
                    List<Future<PredictionRecord>> fs = new ArrayList<>();
                    long t0 = System.nanoTime();
                    for (int i = 0; i < c.throughputRequests(); i++) {
                        Item it = its.get((offset + i) % its.size());
                        fs.add(pool.submit(() -> call(ctx, m, it, PredictionRecord.THROUGHPUT, 1, conc)));
                    }
                    int errors = 0;
                    for (Future<PredictionRecord> f : fs) {
                        if (!f.get().outcome().equals(dev.beyondllm.domain.Outcome.SUCCESS)) {
                            errors++;
                        }
                    }
                    double wall = (System.nanoTime() - t0) / 1e9;
                    pool.shutdown();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("concurrency", conc);
                    row.put("requests", c.throughputRequests());
                    row.put("wallSeconds", Math.round(wall * 1000) / 1000.0);
                    row.put("errors", errors);
                    tp.add(row);
                }
            }
            mr.put("throughput", tp);
            mr.put("ollamaLoaded", ollamaLoaded(needed));
            mr.put("resources", sampler.stop());
        }
        mr.put("modelStorageBytes", storageBytes(m));
    }

    private List<Map<String, Object>> ollamaLoaded(List<String> needed) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode n : ollama.loaded()) {
            if (needed.contains(n.path("name").asText())) {
                out.add(Map.of("name", n.path("name").asText(), "sizeBytes", n.path("size").asLong(), "sizeVramBytes", n.path("size_vram").asLong(),
                        "contextLength", n.path("context_length").asLong(0)));
            }
        }
        return out;
    }

    private Long storageBytes(BenchmarkModel m) {
        if (m.runtimeModels().isEmpty()) {
            Object s = m.health().info().get("storage_bytes");
            return s instanceof Number n ? n.longValue() : null;
        }
        if (m.info().kind().equals("pipeline")) {
            return null;
        }
        JsonNode t = ollama.tags().get(m.runtimeModels().getFirst());
        return t == null ? null : t.path("size").asLong();
    }

    private PredictionRecord call(Ctx ctx, BenchmarkModel m, Item it, String phase, int rep, int conc) {
        DatasetRecord r = it.record();
        BenchmarkRequest req = new BenchmarkRequest(r.id(), it.task(), r.text(), r.language());
        BenchmarkResponse resp;
        try {
            resp = m.execute(req);
        } catch (RuntimeException e) { // adapters must not throw; guard anyway so nothing is silently lost
            resp = BenchmarkResponse.failure(m.id(), req, dev.beyondllm.domain.Outcome.INFRA_ERROR, "Adapter threw " + e, null, null);
        }
        PredictionRecord pr = new PredictionRecord(ctx.runId, m.id(), r.id(), r.group(), r.language(), it.task(), phase, rep, conc,
                resp.outcome(), resp.error(), r.text(), r.gold(), r.meta(), resp.prediction(), Evaluator.evaluate(r, it.task(), resp),
                resp.timing(), resp.rawResponse(), resp.metadata(), Instant.now());
        store.append(ctx.runId, pr);
        long d = ctx.done.incrementAndGet();
        if (d % 10 == 0 || d == ctx.total) {
            ctx.publish(RunStatus.State.RUNNING, null);
        }
        return pr;
    }

    public void regenerateReport(String runId) {
        RunStatus s = status(runId).orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> env = store.readJson(runId, "environment.json", Map.class).orElse(Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> modelRuns = store.readJson(runId, "model-runs.json", Map.class).orElse(Map.of());
        List<PredictionRecord> preds = store.predictions(runId);
        Map<String, Object> report = ReportBuilder.build(s, env, modelRuns, preds);
        store.writeJson(runId, "report.json", report);
        store.writeText(runId, "report.md", MarkdownReport.render(report));
        store.writeCsv(runId, preds);
    }
}

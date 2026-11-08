package dev.beyondllm.benchmark;

import dev.beyondllm.application.BenchmarkModel;
import dev.beyondllm.application.ModelInfo;
import dev.beyondllm.application.ModelRegistry;
import dev.beyondllm.dataset.DatasetRepository;
import dev.beyondllm.domain.BenchmarkRequest;
import dev.beyondllm.domain.BenchmarkResponse;
import dev.beyondllm.domain.Capability;
import dev.beyondllm.domain.HealthStatus;
import dev.beyondllm.domain.LabelPrediction;
import dev.beyondllm.domain.LabelSchema;
import dev.beyondllm.domain.ModelCapabilities;
import dev.beyondllm.domain.Outcome;
import dev.beyondllm.domain.Prediction;
import dev.beyondllm.domain.TaskType;
import dev.beyondllm.domain.Timing;
import dev.beyondllm.infrastructure.hybrid.HybridThresholds;
import dev.beyondllm.infrastructure.ollama.OllamaAdminClient;
import dev.beyondllm.infrastructure.persistence.ResultStore;
import dev.beyondllm.infrastructure.system.EnvironmentInspector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkRunnerTest {

    /** Deterministic fake classifier: always says "refund" with probability 0.9, fails every 5th call with a timeout. */
    static final class FakeClassifier implements BenchmarkModel {
        final AtomicInteger calls = new AtomicInteger();

        public String id() {
            return "fake-cls";
        }

        public ModelInfo info() {
            return new ModelInfo(id(), "Fake", "model", "test", "fake", "test", null, null, "");
        }

        public ModelCapabilities capabilities() {
            return new ModelCapabilities(EnumSet.of(Capability.CLASSIFICATION, Capability.LABEL_CONFIDENCE), EnumSet.of(TaskType.CLASSIFICATION), null);
        }

        public HealthStatus health() {
            return HealthStatus.up(id(), "ok", Map.of());
        }

        public BenchmarkResponse execute(BenchmarkRequest r) {
            if (calls.incrementAndGet() % 5 == 0) {
                return BenchmarkResponse.failure(id(), r, Outcome.TIMEOUT, "fake timeout", Timing.client(1_000_000), null);
            }
            Map<String, LabelPrediction> l = new LinkedHashMap<>();
            for (String f : LabelSchema.FIELDS) {
                l.put(f, new LabelPrediction(f.equals("intent") ? "refund" : LabelSchema.labels(f).getFirst(), 0.9, null, null));
            }
            return BenchmarkResponse.success(id(), r, Prediction.of(l, null), Timing.client(2_000_000), "{}", Map.of());
        }
    }

    static final class DownModel extends FakeClassifierBase {
        public String id() {
            return "down";
        }

        public HealthStatus health() {
            return HealthStatus.of(id(), HealthStatus.State.DOWN, "not running");
        }
    }

    abstract static class FakeClassifierBase implements BenchmarkModel {
        public ModelInfo info() {
            return new ModelInfo(id(), "x", "model", "t", "x", "t", null, null, "");
        }

        public ModelCapabilities capabilities() {
            return new ModelCapabilities(EnumSet.of(Capability.CLASSIFICATION), EnumSet.allOf(TaskType.class), null);
        }

        public BenchmarkResponse execute(BenchmarkRequest r) {
            throw new AssertionError("must not be called");
        }
    }

    @Test
    void runsPhasesPersistsEverythingAndCountsFailures(@TempDir Path tmp) throws Exception {
        FakeClassifier fake = new FakeClassifier();
        ModelRegistry registry = new ModelRegistry(List.of(fake, new DownModel()));
        DatasetRepository datasets = new DatasetRepository(tmp.resolve("datasets").toString()); // generated in memory
        ResultStore store = new ResultStore(tmp.resolve("results").toString());
        OllamaAdminClient ollama = new OllamaAdminClient("http://127.0.0.1:1");
        BenchmarkRunner runner = new BenchmarkRunner(registry, datasets, store, new EnvironmentInspector(ollama, "http://127.0.0.1:1"), ollama,
                new HybridThresholds(0.8, 0.8, tmp.toString()));

        BenchmarkConfig cfg = new BenchmarkConfig(List.of("fake-cls", "down"), "coffee-v1", List.of("classification", "extraction", "combined"),
                "evaluation", null, 30, 2, 2, 5, List.of(1, 2), 4, 10, true, 42L, "test");
        RunStatus s = runner.runSync(cfg);

        assertEquals(RunStatus.State.COMPLETED, s.status());
        assertTrue(s.errors().stream().anyMatch(e -> e.contains("down") && e.contains("DOWN")), "unavailable runtime reported cleanly");
        List<PredictionRecord> preds = store.predictions(s.runId());
        long quality = preds.stream().filter(p -> p.phase().equals(PredictionRecord.QUALITY)).count();
        // extraction records are incompatible with a classification-only model; combined ones are sent as CLASSIFICATION
        assertEquals(20, quality);
        assertTrue(preds.stream().filter(p -> p.group().equals("combined")).allMatch(p -> p.task() == TaskType.CLASSIFICATION));
        assertEquals(2, preds.stream().filter(p -> p.phase().equals(PredictionRecord.WARMUP)).count());
        java.util.Set<String> measured = new java.util.HashSet<>();
        preds.stream().filter(p -> !p.phase().equals(PredictionRecord.WARMUP)).forEach(p -> measured.add(p.recordId()));
        assertTrue(preds.stream().filter(p -> p.phase().equals(PredictionRecord.WARMUP)).noneMatch(p -> measured.contains(p.recordId())),
                "warm-up must not reuse inputs that are measured later (prompt-cache contamination)");
        assertEquals(5, preds.stream().filter(p -> p.phase().equals(PredictionRecord.LATENCY)).count());
        assertEquals(8, preds.stream().filter(p -> p.phase().equals(PredictionRecord.THROUGHPUT)).count());
        assertEquals(1, preds.stream().filter(p -> p.phase().equals(PredictionRecord.COLD)).count());

        Path dir = store.runDir(s.runId());
        for (String f : List.of("status.json", "environment.json", "predictions.jsonl", "predictions.csv", "report.json", "report.md", "model-runs.json")) {
            assertTrue(Files.exists(dir.resolve(f)), f);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> report = store.readJson(s.runId(), "report.json", Map.class).orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> m = ((List<Map<String, Object>>) report.get("models")).stream().filter(x -> x.get("modelId").equals("fake-cls")).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) m.get("counts");
        long timeouts = quality - ((Number) counts.get("success")).longValue();
        assertTrue(timeouts > 0, "some injected timeouts landed in the quality phase");
        @SuppressWarnings("unchecked")
        Map<String, Object> cls = (Map<String, Object>) m.get("classification");
        assertEquals(20, ((Number) cls.get("n")).intValue(), "timeouts stay in the denominator");
        assertNull(m.get("extraction"));
        @SuppressWarnings("unchecked")
        Map<String, Object> cal = (Map<String, Object>) m.get("calibration");
        assertEquals(true, cal.get("available"));
        assertNotNull(m.get("latency"));
        assertEquals(2, ((List<?>) m.get("throughput")).size());
        assertTrue(Files.readString(dir.resolve("report.md")).contains("Synthetic benchmark"));
        String csv = Files.readString(dir.resolve("predictions.csv"));
        assertTrue(csv.startsWith("run_id,model_id,record_id"));
    }

    @Test
    void requestTaskMapping() {
        var cls = new ModelCapabilities(EnumSet.of(Capability.CLASSIFICATION), EnumSet.of(TaskType.CLASSIFICATION), null);
        assertEquals(TaskType.CLASSIFICATION, BenchmarkRunner.requestTask(cls, TaskType.COMBINED).orElseThrow());
        assertTrue(BenchmarkRunner.requestTask(cls, TaskType.EXTRACTION).isEmpty());
    }

    @Test
    void csvEscaping() {
        assertEquals("\"a,b\"", csv("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", csv("say \"hi\""));
    }

    private static String csv(String v) {
        try {
            var m = ResultStore.class.getDeclaredMethod("csv", String.class);
            m.setAccessible(true);
            return (String) m.invoke(null, v);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}

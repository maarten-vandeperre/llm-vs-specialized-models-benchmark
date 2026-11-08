package dev.beyondllm.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.beyondllm.benchmark.PredictionRecord;
import dev.beyondllm.benchmark.RunStatus;
import dev.beyondllm.domain.LabelSchema;
import dev.beyondllm.infrastructure.http.JsonHttp;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * File-based persistence: {@code results/runs/<runId>/} with
 * {@code status.json}, {@code environment.json}, {@code predictions.jsonl}, {@code predictions.csv},
 * {@code resources.json}, {@code report.json} and {@code report.md}. No database required.
 */
@ApplicationScoped
public class ResultStore {

    private static final Logger LOG = Logger.getLogger(ResultStore.class);
    private final ObjectMapper mapper = JsonHttp.MAPPER.copy().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final ObjectMapper pretty = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    private final Path runsDir;

    public ResultStore(@ConfigProperty(name = "bench.results-dir", defaultValue = "../results") String resultsDir) {
        this.runsDir = ProjectPaths.resolve(resultsDir).resolve("runs");
    }

    public Path runDir(String runId) {
        if (!runId.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid run id");
        }
        return runsDir.resolve(runId);
    }

    public synchronized void append(String runId, PredictionRecord r) {
        try {
            Path f = runDir(runId).resolve("predictions.jsonl");
            Files.createDirectories(f.getParent());
            Files.writeString(f, mapper.writeValueAsString(r) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<PredictionRecord> predictions(String runId) {
        Path f = runDir(runId).resolve("predictions.jsonl");
        if (!Files.exists(f)) {
            return List.of();
        }
        List<PredictionRecord> out = new ArrayList<>();
        try (Stream<String> lines = Files.lines(f, StandardCharsets.UTF_8)) {
            lines.filter(l -> !l.isBlank()).forEach(l -> {
                try {
                    out.add(mapper.readValue(l, PredictionRecord.class));
                } catch (IOException e) {
                    LOG.warnf("Skipping corrupt line in %s: %s", f, e.getMessage());
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    public void writeJson(String runId, String file, Object value) {
        try {
            Path f = runDir(runId).resolve(file);
            Files.createDirectories(f.getParent());
            pretty.writeValue(f.toFile(), value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public <T> Optional<T> readJson(String runId, String file, Class<T> type) {
        Path f = runDir(runId).resolve(file);
        if (!Files.exists(f)) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(f.toFile(), type));
        } catch (IOException e) {
            LOG.warnf("Cannot read %s: %s", f, e.getMessage());
            return Optional.empty();
        }
    }

    public void writeText(String runId, String file, String text) {
        try {
            Path f = runDir(runId).resolve(file);
            Files.createDirectories(f.getParent());
            Files.writeString(f, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<String> readText(String runId, String file) {
        Path f = runDir(runId).resolve(file);
        try {
            return Files.exists(f) ? Optional.of(Files.readString(f)) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public List<RunStatus> listStatuses() {
        if (!Files.isDirectory(runsDir)) {
            return List.of();
        }
        try (Stream<Path> dirs = Files.list(runsDir)) {
            return dirs.filter(Files::isDirectory)
                    .map(d -> readJson(d.getFileName().toString(), "status.json", RunStatus.class))
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing(RunStatus::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Flat CSV export (one row per prediction) for spreadsheets / pandas. */
    public String toCsv(List<PredictionRecord> records) {
        StringBuilder sb = new StringBuilder();
        List<String> header = new ArrayList<>(List.of("run_id", "model_id", "record_id", "group", "language", "task", "phase",
                "repetition", "concurrency", "outcome", "correct"));
        for (String f : LabelSchema.FIELDS) {
            header.addAll(List.of(f + "_gold", f + "_pred", f + "_prob"));
        }
        header.addAll(List.of("extraction_exact", "extraction_tp", "extraction_fp", "extraction_fn", "hallucinated", "decision_gold",
                "decision_pred", "client_ms", "server_ms", "load_ms", "input_tokens", "output_tokens", "generation_ms", "error"));
        sb.append(String.join(",", header)).append('\n');
        for (PredictionRecord r : records) {
            List<String> row = new ArrayList<>();
            row.add(r.runId());
            row.add(r.modelId());
            row.add(r.recordId());
            row.add(r.group());
            row.add(r.language());
            row.add(String.valueOf(r.task()));
            row.add(r.phase());
            row.add(String.valueOf(r.repetition()));
            row.add(String.valueOf(r.concurrency()));
            row.add(String.valueOf(r.outcome()));
            row.add(r.evaluation() == null ? "" : String.valueOf(r.evaluation().correct()));
            for (String f : LabelSchema.FIELDS) {
                var fe = r.evaluation() == null || r.evaluation().classification() == null ? null : r.evaluation().classification().get(f);
                row.add(fe == null ? "" : fe.gold());
                row.add(fe == null ? "" : fe.predicted());
                row.add(fe == null || fe.probability() == null ? "" : String.valueOf(fe.probability()));
            }
            var ex = r.evaluation() == null ? null : r.evaluation().extraction();
            row.add(ex == null ? "" : String.valueOf(ex.exactMatch()));
            row.add(ex == null ? "" : String.valueOf(ex.tp()));
            row.add(ex == null ? "" : String.valueOf(ex.fp()));
            row.add(ex == null ? "" : String.valueOf(ex.fn()));
            row.add(ex == null ? "" : String.join("|", ex.hallucinated()));
            var de = r.evaluation() == null ? null : r.evaluation().decision();
            row.add(de == null ? "" : de.gold());
            row.add(de == null || de.predicted() == null ? "" : de.predicted());
            var t = r.timing();
            row.add(t == null ? "" : String.format(java.util.Locale.ROOT, "%.3f", t.clientNanos() / 1e6));
            row.add(t == null || t.serverNanos() == null ? "" : String.format(java.util.Locale.ROOT, "%.3f", t.serverNanos() / 1e6));
            row.add(t == null || t.loadNanos() == null ? "" : String.format(java.util.Locale.ROOT, "%.3f", t.loadNanos() / 1e6));
            row.add(t == null || t.inputTokens() == null ? "" : String.valueOf(t.inputTokens()));
            row.add(t == null || t.outputTokens() == null ? "" : String.valueOf(t.outputTokens()));
            row.add(t == null || t.generationNanos() == null ? "" : String.format(java.util.Locale.ROOT, "%.3f", t.generationNanos() / 1e6));
            row.add(r.error() == null ? "" : r.error());
            sb.append(String.join(",", row.stream().map(ResultStore::csv).toList())).append('\n');
        }
        return sb.toString();
    }

    static String csv(String v) {
        if (v == null) {
            return "";
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"").replace("\n", " ") + "\"";
        }
        return v;
    }

    public void writeCsv(String runId, List<PredictionRecord> records) {
        writeText(runId, "predictions.csv", toCsv(records));
    }

    public Map<String, Object> describe() {
        return Map.of("runsDir", runsDir.toAbsolutePath().normalize().toString());
    }
}

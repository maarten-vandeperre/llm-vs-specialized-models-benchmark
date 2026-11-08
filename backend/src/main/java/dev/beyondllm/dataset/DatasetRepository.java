package dev.beyondllm.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.beyondllm.domain.DecisionPolicy;
import dev.beyondllm.infrastructure.http.JsonHttp;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads datasets from {@code datasets/} (JSONL). {@code coffee-v1} is generated deterministically if the file is
 * missing; {@code edge-cases-v1} is hand-authored ({@code datasets/fixtures/edge-cases-v1.jsonl}).
 */
@ApplicationScoped
public class DatasetRepository {

    private static final Logger LOG = Logger.getLogger(DatasetRepository.class);
    private static final ObjectMapper M = JsonHttp.MAPPER;

    public record DatasetDescriptor(String name, String description, String version, int records, Long seed, String sha256,
                                    Map<String, Long> groups, Map<String, Long> languages, Map<String, Long> splits) {
    }

    public record Filter(List<String> groups, String split, List<String> languages, Integer limit) {
        public static Filter all() {
            return new Filter(null, null, null, null);
        }
    }

    private final Path datasetsDir;
    private final Map<String, List<DatasetRecord>> cache = new ConcurrentHashMap<>();

    public DatasetRepository(@ConfigProperty(name = "bench.datasets-dir", defaultValue = "../datasets") String dir) {
        this.datasetsDir = dev.beyondllm.infrastructure.persistence.ProjectPaths.resolve(dir);
    }

    public static Path coffeeFile(Path datasetsDir) {
        return datasetsDir.resolve("evaluation").resolve(DatasetGenerator.DATASET_NAME + ".jsonl");
    }

    public static Path edgeCasesFile(Path datasetsDir) {
        return datasetsDir.resolve("fixtures").resolve("edge-cases-v1.jsonl");
    }

    public List<String> names() {
        return List.of(DatasetGenerator.DATASET_NAME, "edge-cases-v1");
    }

    public List<DatasetRecord> load(String name) {
        return cache.computeIfAbsent(name, n -> switch (n) {
            case DatasetGenerator.DATASET_NAME -> {
                Path f = coffeeFile(datasetsDir);
                if (Files.exists(f)) {
                    yield read(f);
                }
                LOG.warnf("%s not found – generating in memory with seed %d", f, DatasetGenerator.DEFAULT_SEED);
                yield new DatasetGenerator(DatasetGenerator.DEFAULT_SEED).generate();
            }
            case "edge-cases-v1" -> {
                Path f = edgeCasesFile(datasetsDir);
                yield Files.exists(f) ? read(f) : List.of();
            }
            default -> throw new IllegalArgumentException("Unknown dataset " + n + ". Known: " + names());
        });
    }

    public List<DatasetRecord> select(String name, Filter f) {
        List<DatasetRecord> out = new ArrayList<>();
        for (DatasetRecord r : load(name)) {
            if (f.groups() != null && !f.groups().isEmpty() && !f.groups().contains(r.group())) {
                continue;
            }
            if (f.split() != null && !f.split().isBlank() && !f.split().equals(r.split())) {
                continue;
            }
            if (f.languages() != null && !f.languages().isEmpty() && !f.languages().contains(r.language())) {
                continue;
            }
            out.add(r);
        }
        if (f.limit() != null && f.limit() > 0 && out.size() > f.limit()) {
            out = stratifiedLimit(out, f.limit());
        }
        return out;
    }

    /** Takes records round-robin across groups so small smoke runs still cover every group. */
    static List<DatasetRecord> stratifiedLimit(List<DatasetRecord> in, int limit) {
        Map<String, List<DatasetRecord>> byGroup = new LinkedHashMap<>();
        in.forEach(r -> byGroup.computeIfAbsent(r.group(), k -> new ArrayList<>()).add(r));
        List<DatasetRecord> out = new ArrayList<>();
        int i = 0;
        while (out.size() < limit) {
            boolean any = false;
            for (List<DatasetRecord> g : byGroup.values()) {
                if (i < g.size() && out.size() < limit) {
                    out.add(g.get(i));
                    any = true;
                }
            }
            if (!any) {
                break;
            }
            i++;
        }
        return out;
    }

    public Optional<DatasetDescriptor> describe(String name) {
        List<DatasetRecord> recs = load(name);
        if (recs.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Long> groups = new TreeMap<>();
        Map<String, Long> langs = new TreeMap<>();
        Map<String, Long> splits = new TreeMap<>();
        for (DatasetRecord r : recs) {
            groups.merge(r.group(), 1L, Long::sum);
            langs.merge(r.language(), 1L, Long::sum);
            splits.merge(r.split(), 1L, Long::sum);
        }
        boolean generated = name.equals(DatasetGenerator.DATASET_NAME);
        return Optional.of(new DatasetDescriptor(name,
                generated ? "Synthetic fictional coffee-shop messages (deterministic generator, seed 42)"
                        : "Hand-authored synthetic edge cases (fictional)",
                name, recs.size(), generated ? DatasetGenerator.DEFAULT_SEED : null, sha256(recs), groups, langs, splits));
    }

    public List<DatasetDescriptor> describeAll() {
        return names().stream().map(this::describe).flatMap(Optional::stream).toList();
    }

    // ---- IO ---------------------------------------------------------------------------------------------

    public static List<DatasetRecord> read(Path f) {
        try {
            List<DatasetRecord> out = new ArrayList<>();
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("//")) {
                    continue;
                }
                DatasetRecord r = M.readValue(line, DatasetRecord.class);
                out.add(complete(r));
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read dataset " + f, e);
        }
    }

    public static void write(List<DatasetRecord> records, Path f) {
        try {
            Files.createDirectories(f.getParent());
            try (BufferedWriter w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
                for (DatasetRecord r : records) {
                    w.write(M.writeValueAsString(r));
                    w.write('\n');
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Fills derivable fields of hand-authored records: spans, decision, split, approxTokens. */
    static DatasetRecord complete(DatasetRecord r) {
        DatasetRecord.Gold g = withSpans(r.gold(), r.text());
        if (g.decision() == null) {
            g = new DatasetRecord.Gold(g.intent(), g.sentiment(), g.category(), g.complaint(), g.orderId(), g.orderIdSpan(),
                    g.items(), g.total(), g.currency(), g.totalSpan(),
                    DecisionPolicy.decide(g.intent(), g.complaint(), g.orderId() != null, g.total()));
        }
        int approx = r.approxTokens() > 0 ? r.approxTokens()
                : (int) Math.round(r.text().split("\\s+").length * DatasetGenerator.TOKENS_PER_WORD);
        return new DatasetRecord(r.id(), r.group(), r.split() == null ? "evaluation" : r.split(), r.language(), r.task(), r.text(),
                approx, r.tags() == null ? List.of() : r.tags(), r.meta() == null ? Map.of() : r.meta(), g);
    }

    /** Computes character spans of the gold order id and total amount (case-insensitive search). */
    public static DatasetRecord.Gold withSpans(DatasetRecord.Gold g, String text) {
        DatasetRecord.SpanRef idSpan = g.orderIdSpan();
        String lower = text.toLowerCase(Locale.ROOT);
        if (idSpan == null && g.orderId() != null) {
            int i = lower.indexOf(g.orderId().toLowerCase(Locale.ROOT));
            if (i >= 0) {
                idSpan = new DatasetRecord.SpanRef(i, i + g.orderId().length());
            }
        }
        DatasetRecord.SpanRef totalSpan = g.totalSpan();
        if (totalSpan == null && g.total() != null) {
            String dot = g.total().setScale(2, RoundingMode.HALF_UP).toPlainString();
            int i = text.indexOf(dot);
            String found = dot;
            if (i < 0) {
                found = dot.replace('.', ',');
                i = text.indexOf(found);
            }
            if (i >= 0) {
                totalSpan = new DatasetRecord.SpanRef(i, i + found.length());
            }
        }
        return new DatasetRecord.Gold(g.intent(), g.sentiment(), g.category(), g.complaint(), g.orderId(), idSpan, g.items(),
                g.total(), g.currency(), totalSpan, g.decision());
    }

    static String sha256(List<DatasetRecord> recs) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            for (DatasetRecord r : recs) {
                d.update(M.writeValueAsBytes(r));
                d.update((byte) '\n');
            }
            return HexFormat.of().formatHex(d.digest());
        } catch (Exception e) {
            return "n/a";
        }
    }

    static BigDecimal scale(BigDecimal b) {
        return b == null ? null : b.setScale(2, RoundingMode.HALF_UP);
    }
}

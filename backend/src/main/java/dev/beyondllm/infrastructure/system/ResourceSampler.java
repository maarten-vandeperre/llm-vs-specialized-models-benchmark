package dev.beyondllm.infrastructure.system;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Samples RSS and CPU% of serving processes whose command line contains a pattern, using {@code ps}
 * (macOS and most Linux distributions). If {@code ps} is unavailable (e.g. remote endpoint, minimal container), the
 * result says so instead of estimating anything.
 * <p>
 * Caveat (documented): on Apple Silicon, memory that Ollama/MLX allocate through Metal is unified memory and may only be
 * partially visible in RSS; Ollama's own {@code /api/ps} size is recorded separately.
 */
public final class ResourceSampler implements AutoCloseable {

    public record Summary(String processPattern, int samples, Double rssMeanMb, Double rssPeakMb, Double cpuMeanPct,
                          Double cpuPeakPct, String note) {
    }

    private final String pattern;
    private final ScheduledExecutorService exec;
    private final List<double[]> samples = new ArrayList<>();
    private volatile String failure;

    public ResourceSampler(String pattern, long intervalMillis) {
        this.pattern = pattern;
        this.exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "resource-sampler");
            t.setDaemon(true);
            return t;
        });
        if (pattern != null) {
            exec.scheduleAtFixedRate(this::sample, 0, intervalMillis, TimeUnit.MILLISECONDS);
        }
    }

    private void sample() {
        try {
            Process p = new ProcessBuilder("ps", "-axo", "pid=,rss=,%cpu=,command=").redirectErrorStream(true).start();
            double rssKb = 0, cpu = 0;
            int matched = 0;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.trim().split("\\s+", 4);
                    if (parts.length < 4 || !matches(parts[3])) {
                        continue;
                    }
                    rssKb += Double.parseDouble(parts[1]);
                    cpu += Double.parseDouble(parts[2].replace(',', '.'));
                    matched++;
                }
            }
            p.waitFor(5, TimeUnit.SECONDS);
            if (matched > 0) {
                synchronized (samples) {
                    samples.add(new double[]{rssKb / 1024.0, cpu});
                }
            }
        } catch (Exception e) {
            failure = "ps unavailable: " + e.getMessage();
        }
    }

    boolean matches(String command) {
        String c = command.toLowerCase(Locale.ROOT);
        if (c.contains("resource-sampler") || c.startsWith("ps ")) {
            return false;
        }
        return switch (pattern) {
            case "ollama" -> c.contains("ollama") && !c.contains("ollama.app/contents/macos/ollama"); // server + runners, not the GUI
            case "uvicorn" -> c.contains("uvicorn") && c.contains("app:app");
            default -> c.contains(pattern.toLowerCase(Locale.ROOT));
        };
    }

    public Summary stop() {
        exec.shutdownNow();
        if (pattern == null) {
            return new Summary(null, 0, null, null, null, null, "not sampled (pipeline or remote endpoint; see stage models)");
        }
        synchronized (samples) {
            if (samples.isEmpty()) {
                return new Summary(pattern, 0, null, null, null, null,
                        failure != null ? failure : "no local process matched '" + pattern + "' (remote endpoint?)");
            }
            double rssSum = 0, rssMax = 0, cpuSum = 0, cpuMax = 0;
            for (double[] s : samples) {
                rssSum += s[0];
                rssMax = Math.max(rssMax, s[0]);
                cpuSum += s[1];
                cpuMax = Math.max(cpuMax, s[1]);
            }
            int n = samples.size();
            return new Summary(pattern, n, r(rssSum / n), r(rssMax), r(cpuSum / n), r(cpuMax),
                    "ps sampling every 500 ms; CPU% is per-core percentage summed over matched processes (can exceed 100)");
        }
    }

    private static double r(double d) {
        return Math.round(d * 10.0) / 10.0;
    }

    @Override
    public void close() {
        exec.shutdownNow();
    }
}

package dev.beyondllm.infrastructure.hybrid;

import com.fasterxml.jackson.databind.JsonNode;
import dev.beyondllm.infrastructure.http.JsonHttp;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Routing thresholds for hybrid pipelines. Defaults come from configuration; when the calibration command has
 * produced {@code results/calibration/thresholds.json} (computed on the calibration split only), those values win.
 */
@ApplicationScoped
public class HybridThresholds {

    private static final Logger LOG = Logger.getLogger(HybridThresholds.class);

    private volatile double kev;
    private volatile double gliner;
    private volatile String source = "configuration defaults";

    public HybridThresholds(@ConfigProperty(name = "bench.hybrid.kev-threshold", defaultValue = "0.80") double kev,
                            @ConfigProperty(name = "bench.hybrid.gliner-threshold", defaultValue = "0.80") double gliner,
                            @ConfigProperty(name = "bench.results-dir", defaultValue = "../results") String resultsDir) {
        this.kev = kev;
        this.gliner = gliner;
        Path f = dev.beyondllm.infrastructure.persistence.ProjectPaths.resolve(resultsDir).resolve("calibration").resolve("thresholds.json");
        if (Files.exists(f)) {
            try {
                JsonNode n = JsonHttp.MAPPER.readTree(f.toFile());
                if (n.has("kev")) {
                    this.kev = n.get("kev").asDouble();
                }
                if (n.has("gliner")) {
                    this.gliner = n.get("gliner").asDouble();
                }
                this.source = f.toAbsolutePath().normalize() + " (calibration split)";
            } catch (Exception e) {
                LOG.warnf("Ignoring unreadable %s: %s", f, e.getMessage());
            }
        }
    }

    public double kev() {
        return kev;
    }

    public double gliner() {
        return gliner;
    }

    public String source() {
        return source;
    }

    public void set(double kevThreshold, double glinerThreshold, String src) {
        this.kev = kevThreshold;
        this.gliner = glinerThreshold;
        this.source = src;
    }

    public Map<String, Object> describe() {
        return Map.of("kev", kev, "gliner", gliner, "source", source);
    }
}

package dev.beyondllm.infrastructure.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import dev.beyondllm.domain.HealthStatus;
import dev.beyondllm.infrastructure.http.JsonHttp;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Ollama management API: version, installed tags/digests, loaded models and unloading (for cold starts). */
@ApplicationScoped
public class OllamaAdminClient {

    private final String baseUrl;
    private final JsonHttp http;

    public OllamaAdminClient(@ConfigProperty(name = "bench.ollama.base-url") String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.http = new JsonHttp(Duration.ofSeconds(3));
    }

    public String baseUrl() {
        return baseUrl;
    }

    public Optional<String> version() {
        JsonHttp.Result r = http.get(baseUrl + "/api/version", Duration.ofSeconds(5));
        if (!r.ok()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(r.json().path("version").asText(null));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Installed models: name → {digest, size, details}. Empty map if Ollama is unreachable. */
    public Map<String, JsonNode> tags() {
        Map<String, JsonNode> out = new LinkedHashMap<>();
        JsonHttp.Result r = http.get(baseUrl + "/api/tags", Duration.ofSeconds(10));
        if (!r.ok()) {
            return out;
        }
        try {
            for (JsonNode m : r.json().path("models")) {
                out.put(m.path("name").asText(), m);
            }
        } catch (Exception ignored) {
            // treat as empty
        }
        return out;
    }

    /** Currently loaded models (/api/ps). */
    public List<JsonNode> loaded() {
        List<JsonNode> out = new ArrayList<>();
        JsonHttp.Result r = http.get(baseUrl + "/api/ps", Duration.ofSeconds(10));
        if (r.ok()) {
            try {
                r.json().path("models").forEach(out::add);
            } catch (Exception ignored) {
                // empty
            }
        }
        return out;
    }

    public Optional<JsonNode> show(String model) {
        JsonHttp.Result r = http.postJson(baseUrl + "/api/show", Map.of("model", model), Duration.ofSeconds(15));
        if (!r.ok()) {
            return Optional.empty();
        }
        try {
            return Optional.of(r.json());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Asks Ollama to unload a model (keep_alive=0). Works for chat models; for decision models it is best effort. */
    public boolean unload(String model) {
        JsonHttp.Result r = http.postJson(baseUrl + "/api/generate", Map.of("model", model, "keep_alive", 0), Duration.ofSeconds(30));
        return r.ok();
    }

    public void unloadAllExcept(List<String> keep) {
        for (JsonNode m : loaded()) {
            String name = m.path("name").asText();
            if (!keep.contains(name)) {
                unload(name);
            }
        }
    }

    /** Health check shared by all Ollama-backed adapters. */
    public HealthStatus health(String modelId, String tag, String minVersion) {
        Optional<String> v = version();
        if (v.isEmpty()) {
            return HealthStatus.of(modelId, HealthStatus.State.DOWN, "Ollama not reachable at " + baseUrl);
        }
        if (minVersion != null && compareVersions(v.get(), minVersion) < 0) {
            return new HealthStatus(modelId, HealthStatus.State.RUNTIME_UNSUPPORTED,
                    "Ollama " + v.get() + " < required " + minVersion, Map.of("ollamaVersion", v.get()));
        }
        Map<String, JsonNode> tags = tags();
        JsonNode t = tags.get(tag);
        if (t == null && !tag.contains(":")) {
            t = tags.get(tag + ":latest");
        }
        if (t == null) {
            return new HealthStatus(modelId, HealthStatus.State.MODEL_MISSING,
                    "Model " + tag + " not installed. Run: ollama pull " + tag, Map.of("ollamaVersion", v.get()));
        }
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("ollamaVersion", v.get());
        info.put("digest", t.path("digest").asText());
        info.put("sizeBytes", t.path("size").asLong());
        info.put("details", t.path("details"));
        return HealthStatus.up(modelId, "Ollama " + v.get(), info);
    }

    public static int compareVersions(String a, String b) {
        String[] pa = a.replaceAll("[^0-9.].*$", "").split("\\.");
        String[] pb = b.replaceAll("[^0-9.].*$", "").split("\\.");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int x = i < pa.length && !pa[i].isEmpty() ? Integer.parseInt(pa[i]) : 0;
            int y = i < pb.length && !pb[i].isEmpty() ? Integer.parseInt(pb[i]) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }
}

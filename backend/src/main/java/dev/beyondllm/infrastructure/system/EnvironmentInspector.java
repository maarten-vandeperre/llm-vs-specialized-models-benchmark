package dev.beyondllm.infrastructure.system;

import com.fasterxml.jackson.databind.JsonNode;
import dev.beyondllm.infrastructure.http.JsonHttp;
import dev.beyondllm.infrastructure.ollama.OllamaAdminClient;
import io.quarkus.runtime.LaunchMode;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Captures the execution environment recorded with every run (hardware, OS, runtimes, model digests).
 * Values that cannot be determined are recorded as "unavailable", never guessed.
 */
@ApplicationScoped
public class EnvironmentInspector {

    private final OllamaAdminClient ollama;
    private final String glinerUrl;

    public EnvironmentInspector(OllamaAdminClient ollama, @ConfigProperty(name = "bench.gliner.base-url") String glinerUrl) {
        this.ollama = ollama;
        this.glinerUrl = glinerUrl;
    }

    public Map<String, Object> capture() {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("timestamp", Instant.now().toString());
        String os = System.getProperty("os.name");
        env.put("os.name", os);
        env.put("os.version", System.getProperty("os.version"));
        env.put("os.arch", System.getProperty("os.arch"));
        env.put("cpu.count", Runtime.getRuntime().availableProcessors());
        boolean mac = os != null && os.toLowerCase().contains("mac");
        if (mac) {
            env.put("macos.productVersion", exec("sw_vers", "-productVersion"));
            env.put("cpu.brand", exec("sysctl", "-n", "machdep.cpu.brand_string"));
            String mem = exec("sysctl", "-n", "hw.memsize");
            env.put("memory.bytes", parseLong(mem));
            env.put("cpu.performanceCores", exec("sysctl", "-n", "hw.perflevel0.physicalcpu"));
            env.put("cpu.efficiencyCores", exec("sysctl", "-n", "hw.perflevel1.physicalcpu"));
            env.put("loadavg", exec("sysctl", "-n", "vm.loadavg"));
            boolean appleSilicon = "aarch64".equals(System.getProperty("os.arch"));
            env.put("appleSilicon", appleSilicon);
            env.put("mlxSupported", appleSilicon ? "yes (Apple Silicon; Ollama MLX runner)" : "no (requires Apple Silicon)");
        } else {
            env.put("cpu.brand", readFirstMatching("/proc/cpuinfo", "model name"));
            env.put("memory.meminfo", readFirstMatching("/proc/meminfo", "MemTotal"));
            env.put("cgroup.memory.max", readFile("/sys/fs/cgroup/memory.max"));
            env.put("cgroup.cpu.max", readFile("/sys/fs/cgroup/cpu.max"));
            env.put("loadavg", readFile("/proc/loadavg"));
            env.put("mlxSupported", "no (not macOS)");
        }
        env.put("gpu", mac ? "Apple integrated GPU (unified memory); utilization not measured (requires root powermetrics)"
                : "not detected by the benchmark (no NVIDIA/ROCm probing)");
        env.put("disk.freeBytes", new File(".").getUsableSpace());
        env.put("java.version", System.getProperty("java.version"));
        env.put("java.vendor", System.getProperty("java.vendor"));
        env.put("java.vm", System.getProperty("java.vm.name"));
        env.put("quarkus.version", quarkusVersion());
        env.put("quarkus.launchMode", LaunchMode.current().name());
        env.put("ollama.baseUrl", ollama.baseUrl());
        env.put("ollama.version", ollama.version().orElse("unavailable"));
        env.put("ollama.numParallel", System.getenv().getOrDefault("OLLAMA_NUM_PARALLEL", "not set (server default)"));
        Map<String, Object> tags = new LinkedHashMap<>();
        ollama.tags().forEach((name, node) -> tags.put(name, Map.of("digest", node.path("digest").asText(), "sizeBytes", node.path("size").asLong(),
                "quantization", node.path("details").path("quantization_level").asText(""),
                "parameterSize", node.path("details").path("parameter_size").asText(""))));
        env.put("ollama.models", tags);
        env.put("gliner.baseUrl", glinerUrl);
        JsonHttp.Result g = new JsonHttp(Duration.ofSeconds(2)).get(glinerUrl.replaceAll("/+$", "") + "/health", Duration.ofSeconds(5));
        if (g.ok()) {
            try {
                JsonNode n = g.json();
                env.put("gliner.versions", JsonHttp.MAPPER.convertValue(n.path("versions"), Map.class));
                env.put("gliner.models", JsonHttp.MAPPER.convertValue(n.path("models"), Map.class));
            } catch (Exception e) {
                env.put("gliner", "unparseable health");
            }
        } else {
            env.put("gliner", "unavailable");
        }
        env.put("synthetic", true);
        return env;
    }

    static String quarkusVersion() {
        try {
            Package p = io.quarkus.runtime.Application.class.getPackage();
            return p.getImplementationVersion() != null ? p.getImplementationVersion() : "unavailable";
        } catch (Exception e) {
            return "unavailable";
        }
    }

    static String exec(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                out = String.join("\n", br.lines().toList()).trim();
            }
            p.waitFor(5, TimeUnit.SECONDS);
            return p.exitValue() == 0 && !out.isEmpty() ? out : "unavailable";
        } catch (Exception e) {
            return "unavailable";
        }
    }

    static Object parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return "unavailable";
        }
    }

    static String readFile(String f) {
        try {
            return Files.readString(Path.of(f)).trim();
        } catch (Exception e) {
            return "unavailable";
        }
    }

    static String readFirstMatching(String f, String prefix) {
        try {
            List<String> lines = Files.readAllLines(Path.of(f));
            return lines.stream().filter(l -> l.startsWith(prefix)).findFirst().map(l -> l.substring(l.indexOf(':') + 1).trim()).orElse("unavailable");
        } catch (Exception e) {
            return "unavailable";
        }
    }
}

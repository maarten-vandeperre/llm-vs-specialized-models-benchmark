package dev.beyondllm.infrastructure.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * Minimal JSON-over-HTTP helper on top of the JDK client. Measures client-side latency around the full exchange
 * and classifies failures without throwing.
 */
public final class JsonHttp {

    public static final ObjectMapper MAPPER = JsonMapper.builder().findAndAddModules()
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private final HttpClient client;

    public JsonHttp(Duration connectTimeout) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .version(HttpClient.Version.HTTP_1_1)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
    }

    public enum Failure { NONE, TIMEOUT, CONNECT, IO }

    /** Result of an HTTP exchange; {@code failure != NONE} means no HTTP response was obtained. */
    public record Result(int status, String body, long nanos, Failure failure, String error) {
        public boolean ok() {
            return failure == Failure.NONE && status >= 200 && status < 300;
        }

        public JsonNode json() throws IOException {
            return MAPPER.readTree(body);
        }

        /** Extracts Ollama/GLiNER style {"error": "..."} messages when present. */
        public String errorMessage() {
            if (error != null) {
                return error;
            }
            try {
                JsonNode n = MAPPER.readTree(body);
                if (n != null && n.path("error").hasNonNull("message")) {
                    return "HTTP " + status + ": " + n.path("error").path("message").asText();
                }
                if (n != null && n.hasNonNull("error")) {
                    return "HTTP " + status + ": " + n.get("error").asText();
                }
                if (n != null && n.hasNonNull("detail")) {
                    return "HTTP " + status + ": " + n.get("detail").toString();
                }
            } catch (Exception ignored) {
                // not JSON
            }
            return "HTTP " + status + ": " + (body == null ? "" : body.substring(0, Math.min(300, body.length())));
        }
    }

    public Result postJson(String url, Object body, Duration timeout) {
        return postJson(url, body, timeout, java.util.Map.of());
    }

    /** POST with extra request headers (e.g. API keys). Headers are never recorded in results. */
    public Result postJson(String url, Object body, Duration timeout, java.util.Map<String, String> headers) {
        try {
            String json = body instanceof String s ? s : MAPPER.writeValueAsString(body);
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json));
            headers.forEach(b::header);
            return send(b.build());
        } catch (IOException e) {
            return new Result(0, null, 0, Failure.IO, "Could not serialize request: " + e.getMessage());
        }
    }

    public Result get(String url, Duration timeout) {
        return get(url, timeout, java.util.Map.of());
    }

    public Result get(String url, Duration timeout, java.util.Map<String, String> headers) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET();
        headers.forEach(b::header);
        return send(b.build());
    }

    private Result send(HttpRequest req) {
        long start = System.nanoTime();
        try {
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            return new Result(resp.statusCode(), resp.body(), System.nanoTime() - start, Failure.NONE, null);
        } catch (HttpTimeoutException e) {
            return new Result(0, null, System.nanoTime() - start, Failure.TIMEOUT, "Timeout after " + req.timeout().orElse(null));
        } catch (ConnectException e) {
            return new Result(0, null, System.nanoTime() - start, Failure.CONNECT, "Connection refused: " + req.uri());
        } catch (IOException e) {
            return new Result(0, null, System.nanoTime() - start, Failure.IO, e.getClass().getSimpleName() + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(0, null, System.nanoTime() - start, Failure.IO, "Interrupted");
        }
    }

    /** Strips Markdown code fences that some generative models wrap around JSON. */
    public static String stripFences(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            t = nl >= 0 ? t.substring(nl + 1) : t.substring(3);
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        return t.trim();
    }
}

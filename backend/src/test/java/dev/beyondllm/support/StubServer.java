package dev.beyondllm.support;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Tiny HTTP stub for contract tests: canned responses per path, records every request body. */
public final class StubServer implements AutoCloseable {

    public record Canned(int status, String body, long delayMillis) {
    }

    private final HttpServer server;
    private final Map<String, Canned> routes = new ConcurrentHashMap<>();
    public final List<String> requests = new CopyOnWriteArrayList<>();
    public final List<String> paths = new CopyOnWriteArrayList<>();

    public StubServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(body);
            paths.add(ex.getRequestURI().getPath());
            Canned c = routes.getOrDefault(ex.getRequestURI().getPath(), new Canned(404, "{\"error\":\"not found\"}", 0));
            if (c.delayMillis() > 0) {
                try {
                    Thread.sleep(c.delayMillis());
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] out = c.body().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(c.status(), out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
    }

    public StubServer on(String path, int status, String body) {
        routes.put(path, new Canned(status, body, 0));
        return this;
    }

    public StubServer onSlow(String path, String body, long delayMillis) {
        routes.put(path, new Canned(200, body, delayMillis));
        return this;
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public String lastRequest() {
        return requests.getLast();
    }

    public static String resource(String name) {
        try (InputStream in = StubServer.class.getResourceAsStream("/recorded/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}

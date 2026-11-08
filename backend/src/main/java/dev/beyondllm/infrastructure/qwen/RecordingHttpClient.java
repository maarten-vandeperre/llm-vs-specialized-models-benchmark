package dev.beyondllm.infrastructure.qwen;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * LangChain4j {@link HttpClient} backed by the JDK client that additionally records the raw response body on the
 * calling thread. LangChain4j's Ollama response mapping drops Ollama's timing fields (total_duration, load_duration,
 * eval_duration); recording the raw body lets the benchmark report server-side durations for Qwen exactly as for
 * the directly-called NuExtract3 adapter, and preserves the raw provider response.
 */
public final class RecordingHttpClient implements HttpClient {

    private static final ThreadLocal<String> LAST_BODY = new ThreadLocal<>();

    private final java.net.http.HttpClient client;
    private final Duration readTimeout;

    private RecordingHttpClient(Duration connectTimeout, Duration readTimeout) {
        this.client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout)
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .build();
        this.readTimeout = readTimeout == null ? Duration.ofSeconds(120) : readTimeout;
    }

    /** Returns and clears the last raw body recorded on this thread. */
    public static String takeLastBody() {
        String b = LAST_BODY.get();
        LAST_BODY.remove();
        return b;
    }

    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) {
        java.net.http.HttpRequest.Builder b = java.net.http.HttpRequest.newBuilder(URI.create(request.url()))
                .timeout(readTimeout)
                .method(request.method().name(), request.body() == null
                        ? java.net.http.HttpRequest.BodyPublishers.noBody()
                        : java.net.http.HttpRequest.BodyPublishers.ofString(request.body()));
        for (Map.Entry<String, List<String>> h : request.headers().entrySet()) {
            for (String v : h.getValue()) {
                if (!h.getKey().equalsIgnoreCase("content-length") && !h.getKey().equalsIgnoreCase("host")) {
                    b.header(h.getKey(), v);
                }
            }
        }
        try {
            HttpResponse<String> resp = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
            LAST_BODY.set(resp.body());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new HttpException(resp.statusCode(), resp.body());
            }
            return SuccessfulHttpResponse.builder().statusCode(resp.statusCode()).headers(resp.headers().map()).body(resp.body()).build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", e);
        }
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
        throw new UnsupportedOperationException("Streaming is not used by the benchmark");
    }

    public static final class Builder implements HttpClientBuilder {
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(120);

        @Override
        public Duration connectTimeout() {
            return connectTimeout;
        }

        @Override
        public HttpClientBuilder connectTimeout(Duration d) {
            if (d != null) {
                connectTimeout = d;
            }
            return this;
        }

        @Override
        public Duration readTimeout() {
            return readTimeout;
        }

        @Override
        public HttpClientBuilder readTimeout(Duration d) {
            if (d != null) {
                readTimeout = d;
            }
            return this;
        }

        @Override
        public HttpClient build() {
            return new RecordingHttpClient(connectTimeout, readTimeout);
        }
    }
}

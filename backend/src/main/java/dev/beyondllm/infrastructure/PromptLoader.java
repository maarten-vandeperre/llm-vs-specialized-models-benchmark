package dev.beyondllm.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Loads version-controlled prompts from {@code src/main/resources/prompts}. Prompt hashes are recorded per run. */
public final class PromptLoader {

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private PromptLoader() {
    }

    public static String load(String name) {
        return CACHE.computeIfAbsent(name, n -> {
            try (InputStream in = PromptLoader.class.getResourceAsStream("/prompts/" + n)) {
                if (in == null) {
                    throw new IllegalStateException("Prompt not found: " + n);
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public static String sha256(String name) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(load(name).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d).substring(0, 16);
        } catch (Exception e) {
            return "n/a";
        }
    }
}

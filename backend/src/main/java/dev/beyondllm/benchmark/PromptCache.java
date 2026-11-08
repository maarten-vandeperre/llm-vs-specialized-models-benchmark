package dev.beyondllm.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects Ollama prompt-cache reuse from a preserved raw provider response.
 *
 * <p>Ollama reports {@code prompt_eval_cached_count}. For Kev's {@code /v1/systemone} endpoint there is no generation
 * step, so a request whose whole prompt is cached is answered in a few milliseconds: that measures a cache lookup,
 * not inference. Generative endpoints always re-process the last few prompt tokens, so for them a reused prompt
 * shows up as "all but a handful of tokens cached" (observed: 1–4 for NuExtract3, 1–8 for Qwen3.5 repeats, while
 * ordinary shared-system-prompt reuse leaves 9+ tokens uncached). A request counts as a <em>full hit</em> when at most
 * {@link #MAX_UNCACHED_TOKENS} prompt tokens were evaluated: the same input was seen before. Full hits are excluded
 * from latency statistics and reported as a count.
 *
 * <p>OpenAI reports {@code usage.prompt_tokens_details.cached_tokens}. Its cache works on prompts of at least 1,024
 * tokens in 128-token increments, so a replayed prompt leaves fewer than {@link #OPENAI_BLOCK} tokens uncached. The
 * same rule (uncached &lt; 128) is applied to those counters. Anthropic only caches with explicit {@code cache_control},
 * which this benchmark never sends.
 *
 * <p>Works on raw payloads of single models and on hybrid payloads that embed stage payloads as escaped JSON strings.
 */
public final class PromptCache {

    private static final Pattern TOTAL = Pattern.compile("(?:input_tokens|prompt_eval_count)\\\\*\"\\s*:\\s*(\\d+)");
    private static final Pattern CACHED = Pattern.compile("prompt_eval_cached_count\\\\*\"\\s*:\\s*(\\d+)");

    private static final Pattern OPENAI_TOTAL = Pattern.compile("\"prompt_tokens\\\\*\"\\s*:\\s*(\\d+)");
    private static final Pattern OPENAI_CACHED = Pattern.compile("\"cached_tokens\\\\*\"\\s*:\\s*(\\d+)");

    /** OpenAI prompt-cache granularity in tokens. */
    public static final long OPENAI_BLOCK = 128;

    /** At most this many uncached prompt tokens still counts as a reused (previously seen) prompt. */
    public static final long MAX_UNCACHED_TOKENS = 8;

    private PromptCache() {
    }

    /** Pairs of (cached, total) prompt tokens found in the payload; each cached count is paired with the nearest preceding total. */
    static List<long[]> pairs(String raw) {
        List<long[]> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        Matcher c = CACHED.matcher(raw);
        while (c.find()) {
            Matcher t = TOTAL.matcher(raw.substring(0, c.start()));
            Long total = null;
            while (t.find()) {
                total = Long.parseLong(t.group(1));
            }
            if (total == null) {
                // Ollama /api/chat puts prompt_eval_count after the cached count in some versions: look ahead.
                Matcher ahead = TOTAL.matcher(raw);
                if (ahead.find(c.end())) {
                    total = Long.parseLong(ahead.group(1));
                }
            }
            if (total != null) {
                out.add(new long[]{Long.parseLong(c.group(1)), total});
            }
        }
        return out;
    }

    /** True when any stage of the request had (all but at most {@link #MAX_UNCACHED_TOKENS} of) its prompt served from the cache. */
    public static boolean fullHit(String raw) {
        for (long[] p : pairs(raw)) {
            if (p[1] > 0 && p[0] > 0 && p[1] - p[0] <= MAX_UNCACHED_TOKENS) {
                return true;
            }
        }
        if (raw != null) {
            Matcher c = OPENAI_CACHED.matcher(raw);
            Matcher t = OPENAI_TOTAL.matcher(raw);
            if (c.find() && t.find()) {
                long cached = Long.parseLong(c.group(1));
                long total = Long.parseLong(t.group(1));
                return cached > 0 && total - cached < OPENAI_BLOCK;
            }
        }
        return false;
    }
}

package dev.beyondllm.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Payload shapes copied from run 20261107-202221-3633. */
class PromptCacheTest {

    @Test
    void kevFullHitIsDetected() {
        String raw = "{\"model\":\"kev:0.8b-mlx-bf16\",\"answers\":{},\"usage\":{\"input_tokens\":523,\"output_tokens\":0},\"prompt_eval_cached_count\":523}";
        assertTrue(PromptCache.fullHit(raw));
    }

    @Test
    void kevPartialReuseIsNotAHit() {
        String raw = "{\"usage\":{\"input_tokens\":523,\"output_tokens\":0},\"prompt_eval_cached_count\":310}";
        assertFalse(PromptCache.fullHit(raw));
    }

    @Test
    void generativePrefixReuseIsNotAHit() {
        String raw = "{\"model\":\"qwen3.5:4b\",\"done\":true,\"prompt_eval_count\":794,\"prompt_eval_cached_count\":357,\"eval_count\":31}";
        assertFalse(PromptCache.fullHit(raw));
        assertEquals(1, PromptCache.pairs(raw).size());
    }

    @Test
    void hybridPayloadWithEscapedStagesIsParsedPerStage() {
        String raw = "{\"kev-0.8b\":\"{\\\"usage\\\":{\\\"input_tokens\\\":447,\\\"output_tokens\\\":0},\\\"prompt_eval_cached_count\\\":447}\","
                + "\"qwen3.5-4b\":\"{\\\"prompt_eval_count\\\":800,\\\"prompt_eval_cached_count\\\":355}\"}";
        assertEquals(2, PromptCache.pairs(raw).size());
        assertTrue(PromptCache.fullHit(raw));
    }

    @Test
    void generativeReplayLeavesOnlyAFewTokensUncached() {
        // nuextract3 quality request long-0080 after the same document was a warm-up request
        String raw = "{\"model\":\"numind/nuextract3:q4_k_m\",\"prompt_eval_count\":4458,\"prompt_eval_cached_count\":4454,\"eval_count\":60}";
        assertTrue(PromptCache.fullHit(raw));
    }

    @Test
    void sharedSystemPromptWithShortMessageIsNotAHit() {
        String raw = "{\"prompt_eval_count\":391,\"prompt_eval_cached_count\":357}";
        assertFalse(PromptCache.fullHit(raw));
    }

    @Test
    void absentCountersMeanNoHit() {
        assertFalse(PromptCache.fullHit(null));
        assertFalse(PromptCache.fullHit("{\"entities\":{}}"));
    }

    @Test
    void openAiReplayedPromptIsAHitButSharedPrefixIsNot() {
        assertTrue(PromptCache.fullHit("{\"usage\":{\"prompt_tokens\":4520,\"completion_tokens\":60,\"prompt_tokens_details\":{\"cached_tokens\":4480}}}"));
        assertFalse(PromptCache.fullHit("{\"usage\":{\"prompt_tokens\":4520,\"completion_tokens\":60,\"prompt_tokens_details\":{\"cached_tokens\":1024}}}"));
        assertFalse(PromptCache.fullHit("{\"usage\":{\"prompt_tokens\":412,\"prompt_tokens_details\":{\"cached_tokens\":0}}}"));
    }
}

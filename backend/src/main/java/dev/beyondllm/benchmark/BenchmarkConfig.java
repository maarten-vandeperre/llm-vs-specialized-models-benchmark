package dev.beyondllm.benchmark;

import java.util.List;

/**
 * Benchmark run configuration. Defaults follow the methodology: 10 warm-up requests, 5 measured repetitions,
 * concurrency 1/4/8, seed 42, evaluation split only.
 *
 * @param repetitions        repetition 1 is the full quality pass; repetitions 2..N re-run the first
 *                           {@code latencySampleSize} records for latency variance
 * @param throughputRequests requests per concurrency level in the throughput phase (0 disables it)
 */
public record BenchmarkConfig(List<String> models, String dataset, List<String> groups, String split, List<String> languages,
                              Integer limit, int warmup, int repetitions, int latencySampleSize, List<Integer> concurrency,
                              int throughputRequests, int timeoutSeconds, boolean coldStart, long seed, String label) {

    public BenchmarkConfig {
        dataset = dataset == null || dataset.isBlank() ? "coffee-v1" : dataset;
        split = split == null ? "evaluation" : split;
        concurrency = concurrency == null || concurrency.isEmpty() ? List.of(1, 4, 8) : List.copyOf(concurrency);
        models = models == null ? List.of() : List.copyOf(models);
        warmup = Math.max(0, warmup);
        repetitions = Math.max(1, repetitions);
        latencySampleSize = Math.max(0, latencySampleSize);
        throughputRequests = Math.max(0, throughputRequests);
        label = label == null ? "" : label;
    }

    public static BenchmarkConfig defaults(List<String> models) {
        return new BenchmarkConfig(models, "coffee-v1", null, "evaluation", null, null, 10, 5, 30, List.of(1, 4, 8), 40, 180, true, 42L, "");
    }

    public static BenchmarkConfig smoke(List<String> models) {
        return new BenchmarkConfig(models, "coffee-v1", null, "evaluation", null, 30, 2, 1, 0, List.of(1), 0, 180, false, 42L, "smoke");
    }
}

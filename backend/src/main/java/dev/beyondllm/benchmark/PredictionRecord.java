package dev.beyondllm.benchmark;

import dev.beyondllm.dataset.DatasetRecord;
import dev.beyondllm.domain.Outcome;
import dev.beyondllm.domain.Prediction;
import dev.beyondllm.domain.TaskType;
import dev.beyondllm.domain.Timing;
import dev.beyondllm.evaluation.RecordEvaluation;

import java.time.Instant;
import java.util.Map;

/** One persisted line of predictions.jsonl. */
public record PredictionRecord(String runId, String modelId, String recordId, String group, String language, TaskType task,
                               String phase, int repetition, int concurrency, Outcome outcome, String error, String text,
                               DatasetRecord.Gold gold, Map<String, Object> recordMeta, Prediction prediction,
                               RecordEvaluation evaluation, Timing timing, String rawResponse, Map<String, Object> metadata,
                               Instant timestamp) {

    public static final String COLD = "cold";
    public static final String WARMUP = "warmup";
    public static final String QUALITY = "quality";
    public static final String LATENCY = "latency";
    public static final String THROUGHPUT = "throughput";
}

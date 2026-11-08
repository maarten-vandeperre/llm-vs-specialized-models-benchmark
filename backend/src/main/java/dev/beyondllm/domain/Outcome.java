package dev.beyondllm.domain;

/** How a single inference attempt ended. Only {@link #SUCCESS} responses carry a prediction. */
public enum Outcome {
    /** Response parsed into a normalized prediction (it may still be wrong). */
    SUCCESS,
    /** Model/pipeline explicitly declined to answer (e.g. hybrid below threshold without fallback, LLM refusal). */
    ABSTAINED,
    /** Task or input (e.g. length) not supported by the candidate. Never sent to the model. */
    UNSUPPORTED,
    /** HTTP call succeeded but the payload could not be parsed into the expected structure. */
    MALFORMED,
    /** Client-side timeout. */
    TIMEOUT,
    /** Connection refused, HTTP 5xx, missing model, etc. */
    INFRA_ERROR
}

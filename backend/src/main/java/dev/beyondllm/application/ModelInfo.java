package dev.beyondllm.application;

/**
 * Descriptive metadata recorded with every run.
 *
 * @param kind        "model" or "pipeline"
 * @param runtime     e.g. "ollama/systemone", "ollama/chat", "ollama/chat via LangChain4j", "gliner2 python service"
 * @param modelRef    exact tag / checkpoint (e.g. kev:0.8b-mlx-bf16, fastino/gliner2.5-base-v1)
 * @param family      architecture family (decision model, span extractor, generative extraction LLM, general LLM)
 * @param quantization e.g. BF16, Q4_K_M, FP32 – as declared by the provider, null if unknown
 * @param processPattern substring used to locate the serving process for RSS/CPU sampling (null = not sampled)
 * @param notes       free text (protocol notes, thinking disabled, ...)
 */
public record ModelInfo(String id, String displayName, String kind, String runtime, String modelRef, String family,
                        String quantization, String processPattern, String notes) {
}

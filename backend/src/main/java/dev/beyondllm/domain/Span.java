package dev.beyondllm.domain;

/** Half-open character span {@code [start, end)} in the request text, with optional provider confidence. */
public record Span(int start, int end, String text, Double confidence) {
}

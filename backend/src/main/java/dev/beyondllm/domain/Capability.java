package dev.beyondllm.domain;

/** Features a model or pipeline can natively offer. Used to decide task compatibility and what metrics are meaningful. */
public enum Capability {
    /** Assigns one of a fixed set of labels. */
    CLASSIFICATION,
    /** Can return arbitrary substrings / generated strings from the input (order ids, product names). */
    ARBITRARY_TEXT_EXTRACTION,
    /** Returns structured, schema-shaped output (nested objects, arrays). */
    STRUCTURED_EXTRACTION,
    /** Can only select among externally supplied candidates (e.g. Kev choosing among regex candidates). */
    CANDIDATE_SELECTION,
    /** Returns a full probability distribution over labels. */
    PROBABILITY_DISTRIBUTION,
    /** Returns a score for the chosen label (not necessarily a calibrated probability). */
    LABEL_CONFIDENCE,
    /** Returns a score per extracted entity. */
    ENTITY_CONFIDENCE,
    /** Returns character offsets of extracted values. */
    OFFSETS,
    /** Vendor claims support for languages beyond English. */
    MULTILINGUAL,
    /** Performs classification + extraction in a single inference call. */
    COMBINED_SINGLE_CALL,
    /** Candidate is a multi-stage pipeline rather than a single model. */
    PIPELINE
}

package com.aether.aether_backend.service.embedding;

/**
 * Turns raw text into a dense float vector. Implementations must be local
 * (CTO constraint: no cloud AI). Switch implementations via the
 * {@code aether.embedding.provider} property, e.g. "fingerprint" (built-in,
 * deterministic, no external model) or future "ollama" (local BGE model).
 */
public interface EmbeddingClient {

    float[] embed(String text);

    int dimensions();
}

package com.aether.aether_backend.service.embedding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Zero-dependency local embedding built on character n-gram hashing followed by
 * L2 normalisation.
 *
 * <p>Deterministic and fully offline - perfect for running the discovery
 * pipeline in dev/CI without a model runtime. To use a real local model (BGE
 * via Ollama), set {@code aether.embedding.provider=ollama} and provide an
 * Ollama-based implementation.
 */
@Component
@ConditionalOnProperty(name = "aether.embedding.provider", havingValue = "fingerprint", matchIfMissing = true)
public class FingerprintEmbeddingClient implements EmbeddingClient {

    public static final int DIMENSIONS = 64;
    private static final int GRAM = 3;

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];
        if (text == null || text.isBlank()) {
            return vector;
        }
        String normalized = text.toLowerCase();
        int length = normalized.length();
        for (int i = 0; i <= length - GRAM; i++) {
            int hash = normalized.substring(i, i + GRAM).hashCode();
            int index = Math.floorMod(hash, DIMENSIONS);
            vector[index] += 1.0f;
        }
        normalize(vector);
        return vector;
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    private void normalize(float[] vector) {
        double sumSquares = 0;
        for (float v : vector) {
            sumSquares += v * v;
        }
        if (sumSquares == 0) {
            return;
        }
        double norm = Math.sqrt(sumSquares);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
    }
}

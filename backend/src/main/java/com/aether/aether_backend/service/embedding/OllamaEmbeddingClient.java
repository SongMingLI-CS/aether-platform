package com.aether.aether_backend.service.embedding;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Local semantic embedding via Ollama's embeddings endpoint, intended for the
 * BGE model family (e.g. {@code bge-m3}, {@code bge-large-zh-v1.5}). Requires a
 * local Ollama server (CTO constraint: no cloud AI).
 *
 * <p>Enable with {@code aether.embedding.provider=ollama} plus the
 * {@code aether.embedding.ollama.*} properties.
 */
@Component
@ConditionalOnProperty(name = "aether.embedding.provider", havingValue = "ollama")
public class OllamaEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingClient.class);

    private final RestClient restClient;
    private final String model;
    private final int dimensions;

    public OllamaEmbeddingClient(RestClient.Builder builder,
                                 @Value("${aether.embedding.ollama.base-url:http://localhost:11434}") String baseUrl,
                                 @Value("${aether.embedding.ollama.model:bge-m3}") String model,
                                 @Value("${aether.embedding.ollama.dimensions:1024}") int dimensions) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.model = model;
        this.dimensions = dimensions;
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[dimensions];
        }
        JsonNode response = restClient.post()
                .uri("/api/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", model, "prompt", text))
                .retrieve()
                .body(JsonNode.class);

        JsonNode embedding = response == null ? null : response.get("embedding");
        if (embedding == null || !embedding.isArray()) {
            log.warn("Ollama returned no 'embedding' array for model '{}': {}", model, response);
            return new float[dimensions];
        }
        float[] vector = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            vector[i] = embedding.get(i).floatValue();
        }
        return vector;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }
}

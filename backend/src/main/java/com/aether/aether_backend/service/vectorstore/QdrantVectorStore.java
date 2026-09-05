package com.aether.aether_backend.service.vectorstore;

import java.util.ArrayList;
import java.util.List;
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
 * Real vector database backend backed by a local Qdrant instance over its REST
 * API. The collection is created lazily on the first {@code upsert} with the
 * vector dimension observed from the embedding, so no separate dimension
 * property is needed.
 *
 * <p>Enable with {@code aether.vectorstore.provider=qdrant} plus the
 * {@code aether.vectorstore.qdrant.*} properties.
 */
@Component
@ConditionalOnProperty(name = "aether.vectorstore.provider", havingValue = "qdrant")
public class QdrantVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(QdrantVectorStore.class);

    private final RestClient restClient;
    private final String collection;
    private volatile Integer ensuredDimensions = null;

    public QdrantVectorStore(RestClient.Builder builder,
                             @Value("${aether.vectorstore.qdrant.base-url:http://localhost:6333}") String baseUrl,
                             @Value("${aether.vectorstore.qdrant.collection:aether_atoms}") String collection) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.collection = collection;
    }

    @Override
    public synchronized void upsert(long atomId, float[] vector) {
        ensureCollection(vector.length);
        restClient.put()
                .uri("/collections/{collection}/points?wait=true", collection)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("points", List.of(Map.of("id", atomId, "vector", toList(vector)))))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public List<ScoredAtom> search(float[] queryVector, int topK) {
        if (ensuredDimensions == null || topK <= 0) {
            return List.of();
        }
        try {
            JsonNode response = restClient.post()
                    .uri("/collections/{collection}/points/search", collection)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("vector", toList(queryVector), "limit", topK))
                    .retrieve()
                    .body(JsonNode.class);
            List<ScoredAtom> hits = new ArrayList<>();
            JsonNode result = response == null ? null : response.get("result");
            if (result != null && result.isArray()) {
                for (JsonNode hit : result) {
                    hits.add(new ScoredAtom(hit.get("id").asLong(), hit.get("score").asDouble()));
                }
            }
            return hits;
        } catch (Exception e) {
            log.warn("Qdrant search failed: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public int size() {
        try {
            JsonNode response = restClient.get()
                    .uri("/collections/{collection}", collection)
                    .retrieve()
                    .body(JsonNode.class);
            return response == null ? 0 : response.path("result").path("points_count").asInt();
        } catch (Exception e) {
            // Collection not yet created, or Qdrant unreachable - treat as empty.
            return 0;
        }
    }

    private void ensureCollection(int dimensions) {
        if (ensuredDimensions != null) {
            return;
        }
        try {
            restClient.put()
                    .uri("/collections/{collection}", collection)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("vectors", Map.of("size", dimensions, "distance", "Cosine")))
                    .retrieve()
                    .toBodilessEntity();
            ensuredDimensions = dimensions;
            log.info("Qdrant collection '{}' ensured with {} dimensions.", collection, dimensions);
        } catch (Exception e) {
            // Leave ensuredDimensions null so the next upsert retries creation.
            log.warn("Qdrant ensure collection failed: {}", e.getMessage());
        }
    }

    private List<Float> toList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float v : vector) {
            list.add(v);
        }
        return list;
    }
}

package com.aether.aether_backend.service.vectorstore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Simple in-process vector store using brute-force cosine similarity.
 * Perfect for dev/CI and small corpora; swap for a real vector database
 * (Qdrant/Milvus) via {@code aether.vectorstore.provider} when scaling up.
 */
@Component
@ConditionalOnProperty(name = "aether.vectorstore.provider", havingValue = "memory", matchIfMissing = true)
public class InMemoryVectorStore implements VectorStore {

    private final Map<Long, float[]> vectors = new ConcurrentHashMap<>();

    @Override
    public void upsert(long atomId, float[] vector) {
        vectors.put(atomId, vector);
    }

    @Override
    public List<ScoredAtom> search(float[] queryVector, int topK) {
        List<ScoredAtom> hits = new ArrayList<>();
        double queryNorm = norm(queryVector);
        for (Map.Entry<Long, float[]> entry : vectors.entrySet()) {
            double score = cosine(queryVector, entry.getValue(), queryNorm);
            hits.add(new ScoredAtom(entry.getKey(), score));
        }
        hits.sort(Comparator.comparingDouble(ScoredAtom::score).reversed());
        return hits.stream().limit(Math.max(topK, 0)).toList();
    }

    @Override
    public int size() {
        return vectors.size();
    }

    private double cosine(float[] a, float[] b, double normA) {
        double normB = norm(b);
        if (normA == 0 || normB == 0) {
            return 0;
        }
        double dot = 0;
        int limit = Math.min(a.length, b.length);
        for (int i = 0; i < limit; i++) {
            dot += a[i] * b[i];
        }
        return dot / (normA * normB);
    }

    private double norm(float[] vector) {
        double sum = 0;
        for (float v : vector) {
            sum += v * v;
        }
        return Math.sqrt(sum);
    }
}

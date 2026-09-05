package com.aether.aether_backend.service.vectorstore;

import java.util.List;

/**
 * Vector index that stores atom embeddings and returns the nearest neighbours
 * for a query vector. Implementations are switchable via the
 * {@code aether.vectorstore.provider} property ("memory" built-in, future
 * "qdrant" / "milvus" for production).
 */
public interface VectorStore {

    void upsert(long atomId, float[] vector);

    void remove(long atomId);

    List<ScoredAtom> search(float[] queryVector, int topK);

    int size();
}

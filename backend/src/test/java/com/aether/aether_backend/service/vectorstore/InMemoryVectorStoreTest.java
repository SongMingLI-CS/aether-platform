package com.aether.aether_backend.service.vectorstore;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class InMemoryVectorStoreTest {

    private final InMemoryVectorStore store = new InMemoryVectorStore();

    @Test
    void search_returnsNearestNeighboursLimitedByTopK() {
        store.upsert(1L, unitVector(0, 1.0f));
        store.upsert(2L, unitVector(1, 1.0f));
        store.upsert(3L, unitVector(2, 1.0f));

        List<ScoredAtom> hits = store.search(unitVector(1, 1.0f), 2);

        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).atomId()).isEqualTo(2L);
        assertThat(hits.get(0).score()).isGreaterThan(0.99);
        assertThat(hits.get(1).atomId()).isIn(1L, 3L);
    }

    @Test
    void size_tracksUpsertedVectors() {
        assertThat(store.size()).isZero();
        store.upsert(1L, unitVector(0, 1.0f));
        store.upsert(1L, unitVector(0, 1.0f));
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void clear_removesAllVectors() {
        store.upsert(1L, unitVector(0, 1.0f));
        store.upsert(2L, unitVector(1, 1.0f));

        store.clear();

        assertThat(store.size()).isZero();
        assertThat(store.search(unitVector(0, 1.0f), 5)).isEmpty();
    }

    private float[] unitVector(int index, float value) {
        float[] vector = new float[4];
        vector[index] = value;
        return vector;
    }
}

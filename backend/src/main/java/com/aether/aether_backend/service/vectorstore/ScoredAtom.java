package com.aether.aether_backend.service.vectorstore;

/**
 * A vector search hit: the atom id and its cosine similarity to the query.
 */
public record ScoredAtom(long atomId, double score) {
}

package com.aether.aether_backend.domain.event;

/**
 * Published after an atom's content changes so the discovery pipeline can
 * re-embed and re-index it (keeping the vector store fresh).
 */
public record AtomUpdatedEvent(Long atomId) {
}

package com.aether.aether_backend.domain.event;

/**
 * Published after an atom is (logically) deleted so the discovery pipeline can
 * remove its vector from the index, preventing connections to deleted atoms.
 */
public record AtomDeletedEvent(Long atomId) {
}

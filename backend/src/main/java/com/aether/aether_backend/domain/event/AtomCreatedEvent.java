package com.aether.aether_backend.domain.event;

/**
 * Published right after a knowledge atom is persisted. Epic 2's proactive
 * connection discovery consumes this event to vectorise + search related atoms.
 */
public record AtomCreatedEvent(Long atomId) {
}

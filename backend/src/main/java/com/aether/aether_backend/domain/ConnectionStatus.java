package com.aether.aether_backend.domain;

/**
 * Lifecycle status of a discovered connection.
 */
public enum ConnectionStatus {
    /** just discovered by the AI pipeline, waiting for user confirmation */
    PENDING,
    /** confirmed by the user */
    CONFIRMED,
    /** dismissed by the user */
    IGNORED
}

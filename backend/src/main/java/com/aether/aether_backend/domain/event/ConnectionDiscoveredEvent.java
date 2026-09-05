package com.aether.aether_backend.domain.event;

import com.aether.aether_backend.dto.ConnectionResponse;

/**
 * Published when the discovery pipeline persists a new PENDING connection, so
 * push channels (e.g. the Electron desktop popup via SSE) can notify the user
 * in near-real-time.
 */
public record ConnectionDiscoveredEvent(ConnectionResponse connection) {
}

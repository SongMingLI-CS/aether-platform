package com.aether.aether_backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.aether.aether_backend.domain.ConnectionOrigin;
import com.aether.aether_backend.domain.ConnectionStatus;
import com.aether.aether_backend.domain.event.ConnectionDiscoveredEvent;
import com.aether.aether_backend.dto.ConnectionResponse;

class ConnectionStreamServiceTest {

    private final ConnectionStreamService service = new ConnectionStreamService();

    @Test
    void subscribe_returnsEmitter() {
        SseEmitter emitter = service.subscribe();

        assertThat(emitter).isNotNull();
        service.shutdown();
    }

    @Test
    void onConnectionDiscovered_withNoSubscribers_isNoOp() {
        ConnectionResponse response = new ConnectionResponse(
                1L, 1L, "note a", 2L, "note b", 0.92,
                ConnectionStatus.PENDING, ConnectionOrigin.AUTO, "similar", Instant.parse("2026-01-01T00:00:00Z"));

        service.onConnectionDiscovered(new ConnectionDiscoveredEvent(response));

        service.shutdown();
    }
}

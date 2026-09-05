package com.aether.aether_backend.dto;

import com.aether.aether_backend.domain.ConnectionStatus;

import jakarta.validation.constraints.NotNull;

/**
 * Request to transition a discovered connection's lifecycle status. Only the
 * manual outcomes of the PENDING state are accepted here (CONFIRMED / IGNORED);
 * PENDING is the discovery-initial value and cannot be set back by a client.
 */
public record ConnectionStatusUpdateRequest(

        @NotNull(message = "status 不能为空")
        ConnectionStatus status) {
}

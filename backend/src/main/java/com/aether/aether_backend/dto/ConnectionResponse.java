package com.aether.aether_backend.dto;

import java.time.Instant;

import com.aether.aether_backend.domain.ConnectionOrigin;
import com.aether.aether_backend.domain.ConnectionStatus;
import com.aether.aether_backend.domain.KnowledgeConnection;

/**
 * Connection view for API consumers, enriched with content snippets of the two
 * related atoms so clients can render "new note -> old note -> similarity".
 */
public record ConnectionResponse(Long id,
                                 Long sourceAtomId, String sourceText,
                                 Long targetAtomId, String targetText,
                                 Double similarity,
                                 ConnectionStatus status,
                                 ConnectionOrigin origin,
                                 String reason,
                                 Instant createdAt) {

    public static ConnectionResponse from(KnowledgeConnection connection,
                                          String sourceText, String targetText) {
        return new ConnectionResponse(
                connection.getId(),
                connection.getSourceAtomId(), sourceText,
                connection.getTargetAtomId(), targetText,
                connection.getSimilarity(),
                connection.getStatus(),
                connection.getOrigin(),
                connection.getReason(),
                connection.getCreatedAt());
    }
}

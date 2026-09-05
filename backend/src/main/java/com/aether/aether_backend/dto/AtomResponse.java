package com.aether.aether_backend.dto;

import java.time.Instant;

import com.aether.aether_backend.domain.ContentType;
import com.aether.aether_backend.domain.KnowledgeAtom;

/**
 * Atom view exposed to API consumers (never the entity itself).
 */
public record AtomResponse(Long id, String contentText, ContentType contentType,
                           Instant createdAt, Instant updatedAt, Long version) {

    public static AtomResponse from(KnowledgeAtom atom) {
        return new AtomResponse(atom.getId(), atom.getContentText(), atom.getContentType(),
                atom.getCreatedAt(), atom.getUpdatedAt(), atom.getVersion());
    }
}

package com.aether.aether_backend.dto;

import com.aether.aether_backend.domain.ContentType;

import jakarta.validation.constraints.Size;

/**
 * Partial update request. Both fields are optional; at least one must be
 * provided (enforced by the service layer).
 */
public record AtomUpdateRequest(

        @Size(max = 10000, message = "contentText 不能超过 10000 字符")
        String contentText,

        ContentType contentType) {

    public boolean isEmpty() {
        return contentText == null && contentType == null;
    }
}

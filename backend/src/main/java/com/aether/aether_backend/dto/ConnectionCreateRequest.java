package com.aether.aether_backend.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Manual connection request (drag &amp; drop): connect two atoms with the highest
 * confidence. The backend normalizes the unordered pair and upserts it.
 */
public record ConnectionCreateRequest(

        @NotNull(message = "sourceAtomId 不能为空")
        Long sourceAtomId,

        @NotNull(message = "targetAtomId 不能为空")
        Long targetAtomId) {
}

package com.aether.aether_backend.dto;

import com.aether.aether_backend.domain.ContentType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create-atom request. Content is intentionally "raw text or markdown" only
 * (CTO constraint: no rich text / WYSIWYG).
 */
public record AtomCreateRequest(

        @NotBlank(message = "contentText 不能为空")
        @Size(max = 10000, message = "contentText 不能超过 10000 字符")
        String contentText,

        @NotNull(message = "contentType 不能为空")
        ContentType contentType) {
}

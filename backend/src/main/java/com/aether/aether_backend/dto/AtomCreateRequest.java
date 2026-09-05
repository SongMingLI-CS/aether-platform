package com.aether.aether_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create-atom request. Content is intentionally "raw text or markdown" only
 * (CTO constraint: no rich text / WYSIWYG).
 */
public record AtomCreateRequest(

        @NotBlank(message = "contentText 不能为空")
        @Size(max = 10000, message = "contentText 不能超过 10000 字符")
        String contentText,

        @NotBlank(message = "contentType 不能为空")
        @Pattern(regexp = "TEXT|MARKDOWN|IMAGE_URL", message = "contentType 仅支持 TEXT / MARKDOWN / IMAGE_URL")
        String contentType) {
}

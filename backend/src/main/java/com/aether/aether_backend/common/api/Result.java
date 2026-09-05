package com.aether.aether_backend.common.api;

import java.time.Instant;

/**
 * Unified API response envelope.
 *
 * <p>code 0 means success; non-zero codes follow {@code ErrorCode}.
 */
public record Result<T>(int code, String message, T data, Instant timestamp) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "OK", data, Instant.now());
    }

    public static Result<Void> ok() {
        return new Result<>(0, "OK", null, Instant.now());
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null, Instant.now());
    }
}

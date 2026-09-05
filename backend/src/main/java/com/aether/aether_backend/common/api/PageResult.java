package com.aether.aether_backend.common.api;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Page-friendly slice returned to API consumers instead of exposing Spring's Page directly.
 */
public record PageResult<T>(long totalElements, int totalPages, int page, int size, List<T> content) {

    public static <S, T> PageResult<T> from(Page<S> page, Function<S, T> mapper) {
        List<T> mapped = page.getContent().stream().map(mapper).toList();
        return new PageResult<>(page.getTotalElements(), page.getTotalPages(),
                page.getNumber(), page.getSize(), mapped);
    }
}

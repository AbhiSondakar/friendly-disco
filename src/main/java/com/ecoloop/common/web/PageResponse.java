package com.ecoloop.common.web;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(
    List<T> content,
    long totalElements,
    int totalPages,
    int number,
    int size
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
            page.getContent(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.getNumber(),
            page.getSize()
        );
    }

    public <U> PageResponse<U> mapContent(Function<? super T, U> converter) {
        return new PageResponse<>(
            content.stream().map(converter).toList(),
            totalElements,
            totalPages,
            number,
            size
        );
    }
}

package com.example.fairnesstracker.common.web;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * One page of a list. Pages are numbered from 0; {@code size} is capped at 100. Our own type, not Spring's
 * Page, so the JSON stays stable whatever Spring Data does.
 */
public record PageResponse<T>(
        List<T> content,
        @Schema(description = "Page number, from 0", example = "0") int page,
        @Schema(description = "Requested page size", example = "20") int size,
        @Schema(example = "137") long totalElements,
        @Schema(example = "7") int totalPages
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}

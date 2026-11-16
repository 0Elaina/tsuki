package com.nep.tsuki.category.model.vo;

import java.time.OffsetDateTime;

import com.nep.tsuki.category.model.entity.Category;

import lombok.Builder;

@Builder
public record CategoryResponse(
        String id,
        String name,
        int sortOrder,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static CategoryResponse of(Category category) {
        return CategoryResponse.builder()
                .id(category.getId().toString())
                .name(category.getName())
                .sortOrder(category.getSortOrder())
                .status(category.getStatus())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}

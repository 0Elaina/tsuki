package com.nep.tsuki.tag.model.vo;

import java.time.OffsetDateTime;

import com.nep.tsuki.tag.model.entity.Tag;

import lombok.Builder;

@Builder
public record TagResponse(
        String id,
        String name,
        int sortOrder,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static TagResponse of(Tag tag) {
        return TagResponse.builder()
                .id(tag.getId().toString())
                .name(tag.getName())
                .sortOrder(tag.getSortOrder())
                .status(tag.getStatus())
                .createdAt(tag.getCreatedAt())
                .updatedAt(tag.getUpdatedAt())
                .build();
    }
}

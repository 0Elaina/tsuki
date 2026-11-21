package com.nep.tsuki.media.image.model.vo;

import java.time.OffsetDateTime;

import lombok.Builder;

@Builder
public record MediaImageUploadResponse(
        String assetId,
        String url,
        String contentType,
        Integer width,
        Integer height,
        OffsetDateTime expiresAt) {

}

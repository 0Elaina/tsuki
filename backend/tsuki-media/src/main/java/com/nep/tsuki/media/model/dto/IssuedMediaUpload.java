package com.nep.tsuki.media.model.dto;

import java.util.Objects;

public record IssuedMediaUpload(
        long assetId,
        PresignedImageUpload upload) {
    public IssuedMediaUpload {
        if (assetId <= 0L) {
            throw new IllegalArgumentException("assetId 必须大于 0: " + assetId);
        }
        Objects.requireNonNull(upload, "upload 不能为空");
    }
}

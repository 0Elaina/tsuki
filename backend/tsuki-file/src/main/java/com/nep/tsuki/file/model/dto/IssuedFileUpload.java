package com.nep.tsuki.file.model.dto;

import java.util.Objects;

public record IssuedFileUpload(
        long assetId,
        PresignedUpload upload) {

    public IssuedFileUpload {
        if (assetId <= 0L) {
            throw new IllegalArgumentException("assetId 必须大于 0: " + assetId);
        }
        Objects.requireNonNull(upload, "upload 不能为空");
    }
}

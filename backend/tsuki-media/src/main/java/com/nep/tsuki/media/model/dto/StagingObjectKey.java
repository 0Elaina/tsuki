package com.nep.tsuki.media.model.dto;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import com.nep.tsuki.common.constants.TimeConstants;
import com.nep.tsuki.media.constants.MediaStorageConstants;
import com.nep.tsuki.media.model.enums.MediaKind;

public record StagingObjectKey(
        MediaKind kind,
        LocalDate dateUtc,
        UUID uploadId) {

    public StagingObjectKey {
        Objects.requireNonNull(kind, "kind 不能为空");
        Objects.requireNonNull(dateUtc, "dateUtc 不能为空");
        Objects.requireNonNull(uploadId, "uploadId 不能为空");
    }

    public String value() {
        return String.join(
                "/",
                MediaStorageConstants.STAGING_ROOT_PREFIX,
                kind.getPathSegment(),
                TimeConstants.DATE_SLASH.format(dateUtc),
                uploadId.toString(),
                MediaStorageConstants.STAGING_SOURCE_FILENAME);
    }

    public static StagingObjectKey forImage(LocalDate dateUtc, UUID uploadId) {
        return new StagingObjectKey(MediaKind.IMAGE, dateUtc, uploadId);
    }
}

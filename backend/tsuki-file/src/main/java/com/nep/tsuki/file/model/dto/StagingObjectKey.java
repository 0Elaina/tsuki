package com.nep.tsuki.file.model.dto;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import com.nep.tsuki.common.constants.TimeConstants;
import com.nep.tsuki.file.constants.FileStorageConstants;
import com.nep.tsuki.file.model.enums.FileKind;

public record StagingObjectKey(
        FileKind kind,
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
                FileStorageConstants.STAGING_ROOT_PREFIX,
                kind.getPathSegment(),
                TimeConstants.DATE_SLASH.format(dateUtc),
                uploadId.toString(),
                FileStorageConstants.STAGING_SOURCE_FILENAME);
    }

    public static StagingObjectKey of(FileKind kind, LocalDate dateUtc, UUID uploadId) {
        return new StagingObjectKey(kind, dateUtc, uploadId);
    }

    public static StagingObjectKey forImage(LocalDate dateUtc, UUID uploadId) {
        return of(FileKind.IMAGE, dateUtc, uploadId);
    }
}

package com.nep.tsuki.file.utils;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import com.nep.tsuki.common.constants.TimeConstants;
import com.nep.tsuki.file.constants.FileStorageConstants;
import com.nep.tsuki.file.model.enums.FileKind;

public final class FileObjectPathUtils {
    private FileObjectPathUtils() {
    }

    /**
     * 生成浏览器直传暂存区的对象键
     * 格式: staging/{kind}/{UTC年}/{UTC月}/{UTC日}/{uuid}/source
     */
    public static String stagingKey(FileKind kind, LocalDate dateUtc, UUID uploadId) {
        Objects.requireNonNull(kind, "kind 不能为空");
        Objects.requireNonNull(dateUtc, "dateUtc 不能为空");
        Objects.requireNonNull(uploadId, "uploadId 不能为空");
        return String.join(
                "/",
                FileStorageConstants.STAGING_ROOT_PREFIX,
                kind.getPathSegment(),
                TimeConstants.DATE_SLASH.format(dateUtc),
                uploadId.toString(),
                FileStorageConstants.STAGING_SOURCE_FILENAME);
    }

    /**
     * 生成已校验私有原件的尝试级对象键
     * 格式: originals/{assetId}/{jobId}/{attempt}/source
     */
    public static String originalKey(long assetId, long jobId, int attemptCount) {
        validatePositive(assetId, "assetId");
        validatePositive(jobId, "jobId");
        validatePositive(attemptCount, "attemptCount");
        return String.join("/",
                FileStorageConstants.ORIGINALS_ROOT_PREFIX,
                String.valueOf(assetId),
                String.valueOf(jobId),
                String.valueOf(attemptCount),
                FileStorageConstants.ORIGINAL_SOURCE_FILENAME);
    }

    /**
     * 生成衍生物产物的尝试级对象键
     * 格式: variants/{assetId}/{variantId}/{jobId}/{attempt}/output
     */
    public static String variantKey(long assetId, long variantId, long jobId, int attemptCount) {
        validatePositive(assetId, "assetId");
        validatePositive(variantId, "variantId");
        validatePositive(jobId, "jobId");
        validatePositive(attemptCount, "attemptCount");
        return String.join("/",
                FileStorageConstants.VARIANTS_ROOT_PREFIX,
                String.valueOf(assetId),
                String.valueOf(variantId),
                String.valueOf(jobId),
                String.valueOf(attemptCount),
                FileStorageConstants.VARIANT_OUTPUT_FILENAME);
    }

    private static void validatePositive(long value, String name) {
        if (value <= 0L) {
            throw new IllegalArgumentException(name + " 必须大于 0: " + value);
        }
    }
}

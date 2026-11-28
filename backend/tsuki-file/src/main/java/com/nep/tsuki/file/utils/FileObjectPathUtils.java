package com.nep.tsuki.file.utils;

import java.util.List;
import java.util.Objects;

import com.nep.tsuki.file.constants.FileStorageConstants;
import com.nep.tsuki.file.model.enums.FileJobType;
import com.nep.tsuki.file.model.enums.ImageContentType;

public final class FileObjectPathUtils {
    private FileObjectPathUtils() {
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

    /**
     * 兼容调用入口（将在任务执行器升级后移除）
     */
    public static String imageOriginalKey(
            long assetId,
            long jobId,
            int attemptCount,
            ImageContentType contentType) {
        Objects.requireNonNull(contentType, "contentType 不能为空");
        return originalKey(assetId, jobId, attemptCount);
    }

    /**
     * 根据任务类型获取单次执行尝试产生的候选对象键（用于租约超时回收清理）
     */
    public static List<String> attemptCandidateKeys(FileJobType jobType, long assetId, long jobId, int attemptCount) {
        Objects.requireNonNull(jobType, "jobType 不能为空");
        return switch (jobType) {
            case PROCESS_IMAGE -> List.of(originalKey(assetId, jobId, attemptCount));
        };
    }

    private static void validatePositive(long value, String name) {
        if (value <= 0L) {
            throw new IllegalArgumentException(name + " 必须大于 0: " + value);
        }
    }
}

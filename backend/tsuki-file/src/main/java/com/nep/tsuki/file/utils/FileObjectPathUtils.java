package com.nep.tsuki.file.utils;

import java.util.Objects;
import java.util.Optional;

import org.springframework.util.StringUtils;

import com.nep.tsuki.file.constants.FileStorageConstants;
import com.nep.tsuki.file.model.dto.StorageObjectRef;
import com.nep.tsuki.file.model.entity.FileJob;
import com.nep.tsuki.file.model.entity.FileVariant;
import com.nep.tsuki.file.model.enums.FileJobType;
import com.nep.tsuki.file.model.enums.FileStorageScope;

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
     * 推导某次任务尝试可能写出的对象存储定位（用于租期超时回收或取消后的补偿清理）
     */
    public static Optional<StorageObjectRef> attemptArtifact(FileJob job, FileVariant variant) {
        Objects.requireNonNull(job, "job 不能为空");
        if (job.getId() == null || job.getAssetId() == null
                || job.getAttemptCount() == null || job.getAttemptCount() <= 0) {
            return Optional.empty();
        }
        Optional<FileJobType> jobTypeOptional = FileJobType.findByName(job.getType());
        if (jobTypeOptional.isEmpty()) {
            return Optional.empty();
        }
        return switch (jobTypeOptional.get()) {
            case VALIDATE_UPLOAD -> Optional.of(new StorageObjectRef(
                    FileStorageScope.PRIVATE,
                    originalKey(job.getAssetId(), job.getId(), job.getAttemptCount())));
            case GENERATE_VARIANT -> {
                if (job.getVariantId() == null || variant == null || !StringUtils.hasText(variant.getStorageScope())) {
                    yield Optional.empty();
                }
                yield Optional.of(new StorageObjectRef(
                        FileStorageScope.valueOf(variant.getStorageScope()),
                        variantKey(job.getAssetId(), job.getVariantId(), job.getId(), job.getAttemptCount())));
            }
            case CLEANUP_ASSET, CLEANUP_VARIANT -> Optional.empty();
        };
    }

    private static void validatePositive(long value, String name) {
        if (value <= 0L) {
            throw new IllegalArgumentException(name + " 必须大于 0: " + value);
        }
    }
}

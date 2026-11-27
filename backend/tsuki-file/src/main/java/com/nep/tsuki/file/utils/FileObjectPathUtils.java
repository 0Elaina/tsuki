package com.nep.tsuki.file.utils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.util.StringUtils;

import com.nep.tsuki.file.constants.FileStorageConstants;
import com.nep.tsuki.file.model.enums.FileJobType;
import com.nep.tsuki.file.model.enums.FileKind;
import com.nep.tsuki.file.model.enums.ImageContentType;
import com.nep.tsuki.file.model.enums.ImageVariantPreset;

public final class FileObjectPathUtils {
    private FileObjectPathUtils() {
    }

    /**
     * 生成归档后的原始文件在对象存储中的通用 Key（带任务尝试后缀以保证重试防覆盖与安全清理）
     * 格式示例: assets/{kind}/{assetId}/original-{jobId}-{attemptCount}.{ext}
     */
    public static String originalKey(
            FileKind kind,
            long assetId,
            long jobId,
            int attemptCount,
            String extension) {
        Objects.requireNonNull(kind, "kind 不能为空");
        if (assetId <= 0L) {
            throw new IllegalArgumentException("assetId 必须大于 0: " + assetId);
        }
        if (jobId <= 0L) {
            throw new IllegalArgumentException("jobId 必须大于 0: " + jobId);
        }
        if (attemptCount <= 0) {
            throw new IllegalArgumentException("attemptCount 必须大于 0: " + attemptCount);
        }
        if (!StringUtils.hasText(extension)) {
            throw new IllegalArgumentException("extension 不能为空");
        }
        String normalizedExt = extension.strip().toLowerCase(Locale.ROOT);
        return String.join("/",
                FileStorageConstants.ASSETS_ROOT_PREFIX,
                kind.getPathSegment(),
                String.valueOf(assetId),
                FileStorageConstants.ORIGINAL_FILENAME + "-" + jobId + "-" + attemptCount + "." + normalizedExt);
    }

    /**
     * 生成按需派生的文件规格在对象存储中的通用版本化 Key（独立存放于 variants 子目录）
     * 格式示例: assets/{kind}/{assetId}/variants/{fileStem}-{versionId}.{ext}
     */
    public static String variantKey(
            FileKind kind,
            long assetId,
            String fileStem,
            String versionId,
            String extension) {
        Objects.requireNonNull(kind, "kind 不能为空");
        if (assetId <= 0L) {
            throw new IllegalArgumentException("assetId 必须大于 0: " + assetId);
        }
        if (!StringUtils.hasText(fileStem)) {
            throw new IllegalArgumentException("fileStem 不能为空");
        }
        if (!StringUtils.hasText(versionId)) {
            throw new IllegalArgumentException("versionId 不能为空");
        }
        if (!StringUtils.hasText(extension)) {
            throw new IllegalArgumentException("extension 不能为空");
        }
        String normalizedExt = extension.strip().toLowerCase(Locale.ROOT);
        return String.join("/",
                FileStorageConstants.ASSETS_ROOT_PREFIX,
                kind.getPathSegment(),
                String.valueOf(assetId),
                FileStorageConstants.VARIANTS_DIR_SEGMENT,
                fileStem.strip() + "-" + versionId.strip() + "." + normalizedExt);
    }

    /**
     * 生成归档后的原始图片在对象存储中的 Key
     * 格式示例: assets/image/{assetId}/original-{jobId}-{attemptCount}.{ext}
     */
    public static String imageOriginalKey(
            long assetId,
            long jobId,
            int attemptCount,
            ImageContentType contentType) {
        Objects.requireNonNull(contentType, "contentType 不能为空");
        return originalKey(FileKind.IMAGE, assetId, jobId, attemptCount, contentType.getExtension());
    }

    /**
     * 生成按需派生的图片规格在对象存储中的版本化 Key
     * 格式示例: assets/image/{assetId}/variants/{fileStem}-{versionId}.{ext}
     */
    public static String imageVariantKey(
            long assetId,
            ImageVariantPreset preset,
            String versionId,
            ImageContentType contentType) {
        Objects.requireNonNull(preset, "preset 不能为空");
        Objects.requireNonNull(contentType, "contentType 不能为空");
        return variantKey(FileKind.IMAGE, assetId, preset.getFileStem(), versionId, contentType.getExtension());
    }

    /**
     * 根据任务类型获取单次执行尝试可能产生的所有候选对象存储 Key（用于租约超时回收清理）
     */
    public static List<String> attemptCandidateKeys(FileJobType jobType, long assetId, long jobId, int attemptCount) {
        Objects.requireNonNull(jobType, "jobType 不能为空");
        return switch (jobType) {
            case PROCESS_IMAGE -> imageAttemptCandidateKeys(assetId, jobId, attemptCount);
        };
    }

    /**
     * 获取图片入库归档任务单次尝试的所有候选原件 Key（仅覆盖原件，不含业务派生规格）
     */
    public static List<String> imageAttemptCandidateKeys(long assetId, long jobId, int attemptCount) {
        return Arrays.stream(ImageContentType.values())
                .map(contentType -> imageOriginalKey(assetId, jobId, attemptCount, contentType))
                .toList();
    }
}

package com.nep.tsuki.media.utils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.springframework.util.StringUtils;

import com.nep.tsuki.media.constants.MediaStorageConstants;
import com.nep.tsuki.media.model.enums.ImageContentType;
import com.nep.tsuki.media.model.enums.ImageVariantPreset;
import com.nep.tsuki.media.model.enums.MediaJobType;
import com.nep.tsuki.media.model.enums.MediaKind;

public final class MediaObjectPathUtils {
    private MediaObjectPathUtils() {
    }

    /**
     * 生成归档后的原始图片在对象存储中的 Key（带任务尝试后缀以保证重试防覆盖与安全清理）
     * 格式示例: assets/image/{assetId}/original-{jobId}-{attemptCount}.{ext}
     */
    public static String imageOriginalKey(
            long assetId,
            long jobId,
            int attemptCount,
            ImageContentType contentType) {
        Objects.requireNonNull(contentType, "contentType 不能为空");
        return String.join("/",
                MediaStorageConstants.ASSETS_ROOT_PREFIX,
                MediaKind.IMAGE.getPathSegment(),
                String.valueOf(assetId),
                MediaStorageConstants.ORIGINAL_FILENAME + "-" + jobId + "-" + attemptCount + "."
                        + contentType.getExtension());
    }

    /**
     * 生成按需派生的图片规格在对象存储中的版本化 Key（与上传任务解耦，独立存放于 variants 子目录）
     * 格式示例: assets/image/{assetId}/variants/{fileStem}-{versionId}.{ext}
     */
    public static String imageVariantKey(
            long assetId,
            ImageVariantPreset preset,
            String versionId,
            ImageContentType contentType) {
        Objects.requireNonNull(preset, "preset 不能为空");
        if (!StringUtils.hasText(versionId)) {
            throw new IllegalArgumentException("versionId 不能为空");
        }
        Objects.requireNonNull(contentType, "contentType 不能为空");
        return String.join("/",
                MediaStorageConstants.ASSETS_ROOT_PREFIX,
                MediaKind.IMAGE.getPathSegment(),
                String.valueOf(assetId),
                MediaStorageConstants.VARIANTS_DIR_SEGMENT,
                preset.getFileStem() + "-" + versionId.strip() + "." + contentType.getExtension());
    }

    /**
     * 根据任务类型获取单次执行尝试可能产生的所有候选对象存储 Key（用于租约超时回收清理）
     */
    public static List<String> attemptCandidateKeys(MediaJobType jobType, long assetId, long jobId, int attemptCount) {
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

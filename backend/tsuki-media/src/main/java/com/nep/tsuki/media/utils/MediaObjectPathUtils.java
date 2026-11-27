package com.nep.tsuki.media.utils;

import java.util.List;
import java.util.Objects;

import com.nep.tsuki.media.constants.MediaStorageConstants;
import com.nep.tsuki.media.model.enums.ImageContentType;
import com.nep.tsuki.media.model.enums.MediaJobType;
import com.nep.tsuki.media.model.enums.MediaKind;
import com.nep.tsuki.media.model.enums.MediaVariantCode;

public final class MediaObjectPathUtils {
    private MediaObjectPathUtils() {
    }

    /**
     * 生成原始图片在对象存储中的 Key
     * 格式示例: sources/image/{assetId}/{jobId}-{attemptCount}/original
     */
    public static String imageOriginalKey(long assetId, long jobId, int attemptCount) {
        return String.join("/",
                MediaStorageConstants.SOURCES_ROOT_PREFIX,
                MediaKind.IMAGE.getPathSegment(),
                String.valueOf(assetId),
                jobId + "-" + attemptCount,
                MediaStorageConstants.ORIGINAL_FILENAME);
    }

    /**
     * 生成处理后图片变体（如预览图、缩略图）在对象存储中的 Key
     * 格式示例: assets/image/{assetId}/{jobId}-{attemptCount}/{variant}.{ext}
     */
    public static String imageVariantKey(
            long assetId,
            long jobId,
            int attemptCount,
            MediaVariantCode variantCode,
            ImageContentType contentType) {
        Objects.requireNonNull(variantCode, "variantCode 不能为空");
        Objects.requireNonNull(contentType, "contentType 不能为空");
        return String.join("/",
                MediaStorageConstants.ASSETS_ROOT_PREFIX,
                MediaKind.IMAGE.getPathSegment(),
                String.valueOf(assetId),
                jobId + "-" + attemptCount,
                variantCode.getFileStem() + "." + contentType.getExtension());
    }

    /**
     * 根据任务类型获取单次执行尝试可能产生的所有候选文件 Key
     */
    public static List<String> attemptCandidateKeys(MediaJobType jobType, long assetId, long jobId, int attemptCount) {
        Objects.requireNonNull(jobType, "jobType 不能为空");
        return switch (jobType) {
            case PROCESS_IMAGE -> imageAttemptCandidateKeys(assetId, jobId, attemptCount);
        };
    }

    /**
     * 获取图片处理任务单次尝试的所有候选文件 Key
     */
    public static List<String> imageAttemptCandidateKeys(long assetId, long jobId, int attemptCount) {
        return List.of(
                imageOriginalKey(assetId, jobId, attemptCount),
                imageVariantKey(assetId, jobId, attemptCount, MediaVariantCode.PREVIEW, ImageContentType.JPEG),
                imageVariantKey(assetId, jobId, attemptCount, MediaVariantCode.PREVIEW, ImageContentType.PNG));
    }
}
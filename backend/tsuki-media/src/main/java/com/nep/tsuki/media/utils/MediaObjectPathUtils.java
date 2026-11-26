package com.nep.tsuki.media.utils;

import java.util.List;
import java.util.Objects;

import com.nep.tsuki.media.constants.MediaStorageConstants;
import com.nep.tsuki.media.model.enums.ImageContentType;
import com.nep.tsuki.media.model.enums.MediaKind;
import com.nep.tsuki.media.model.enums.MediaVariantCode;

public final class MediaObjectPathUtils {
    private MediaObjectPathUtils() {
    }

    public static String imageOriginalKey(long assetId, long jobId, int attemptCount) {
        return String.join("/",
                MediaStorageConstants.SOURCES_ROOT_PREFIX,
                MediaKind.IMAGE.getPathSegment(),
                String.valueOf(assetId),
                jobId + "-" + attemptCount,
                MediaStorageConstants.ORIGINAL_FILENAME);
    }

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

    public static List<String> imageAttemptCandidateKeys(long assetId, long jobId, int attemptCount) {
        return List.of(
                imageOriginalKey(assetId, jobId, attemptCount),
                imageVariantKey(assetId, jobId, attemptCount, MediaVariantCode.PREVIEW, ImageContentType.JPEG),
                imageVariantKey(assetId, jobId, attemptCount, MediaVariantCode.PREVIEW, ImageContentType.PNG));
    }
}

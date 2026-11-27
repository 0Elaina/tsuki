package com.nep.tsuki.media.processing;

import org.springframework.util.StringUtils;

public record MediaJobCode(
        String code,
        boolean retryable) {

    public static final MediaJobCode SUCCESS = new MediaJobCode("SUCCESS", false);

    public static final MediaJobCode SOURCE_MISSING = new MediaJobCode("SOURCE_MISSING", false);
    public static final MediaJobCode SOURCE_CHANGED = new MediaJobCode("SOURCE_CHANGED", false);
    public static final MediaJobCode IMAGE_TOO_LARGE = new MediaJobCode("IMAGE_TOO_LARGE", false);
    public static final MediaJobCode INVALID_IMAGE = new MediaJobCode("INVALID_IMAGE", false);

    public static final MediaJobCode STORAGE_ERROR = new MediaJobCode("STORAGE_ERROR", true);
    public static final MediaJobCode LEASE_EXPIRED = new MediaJobCode("LEASE_EXPIRED", true);

    public MediaJobCode {
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("code 不能为空");
        }
    }

    public boolean isSuccess() {
        return SUCCESS.equals(this);
    }
}

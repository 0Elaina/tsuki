package com.nep.tsuki.file.processing;

import org.springframework.util.StringUtils;

public record FileJobCode(
        String code,
        boolean retryable,
        boolean contentRejected) {

    public static final FileJobCode SUCCESS = new FileJobCode("SUCCESS", false, false);

    public static final FileJobCode SOURCE_MISSING = new FileJobCode("SOURCE_MISSING", false, false);
    public static final FileJobCode SOURCE_CHANGED = new FileJobCode("SOURCE_CHANGED", false, false);
    public static final FileJobCode IMAGE_TOO_LARGE = new FileJobCode("IMAGE_TOO_LARGE", false, true);
    public static final FileJobCode INVALID_IMAGE = new FileJobCode("INVALID_IMAGE", false, true);
    public static final FileJobCode UNSUPPORTED_PRESET = new FileJobCode("UNSUPPORTED_PRESET", false, false);

    public static final FileJobCode STORAGE_ERROR = new FileJobCode("STORAGE_ERROR", true, false);
    public static final FileJobCode LEASE_EXPIRED = new FileJobCode("LEASE_EXPIRED", true, false);

    public FileJobCode {
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("code 不能为空");
        }
    }

    public boolean isSuccess() {
        return SUCCESS.equals(this);
    }
}

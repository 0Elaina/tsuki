package com.nep.tsuki.file.processing;

import org.springframework.util.StringUtils;

/**
 * 文件异步处理结果码，区分成功、可重试临时故障与不可重试确定性错误
 *
 * @param code      稳定错误码字符串
 * @param retryable 是否允许进入重试退避流程
 */
public record FileJobCode(
        String code,
        boolean retryable) {

    public static final FileJobCode SUCCESS = new FileJobCode("SUCCESS", false);

    public static final FileJobCode SOURCE_MISSING = new FileJobCode("SOURCE_MISSING", false);
    public static final FileJobCode SOURCE_CHANGED = new FileJobCode("SOURCE_CHANGED", false);
    public static final FileJobCode IMAGE_TOO_LARGE = new FileJobCode("IMAGE_TOO_LARGE", false);
    public static final FileJobCode INVALID_IMAGE = new FileJobCode("INVALID_IMAGE", false);
    public static final FileJobCode UNSUPPORTED_PRESET = new FileJobCode("UNSUPPORTED_PRESET", false);

    public static final FileJobCode STORAGE_ERROR = new FileJobCode("STORAGE_ERROR", true);
    public static final FileJobCode LEASE_EXPIRED = new FileJobCode("LEASE_EXPIRED", true);

    public FileJobCode {
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("code 不能为空");
        }
    }

    /**
     * 判断当前结果码是否为成功
     *
     * @return 成功返回 {@code true}
     */
    public boolean isSuccess() {
        return SUCCESS.equals(this);
    }
}

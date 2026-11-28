package com.nep.tsuki.file.model.dto;

import java.util.Objects;

import org.springframework.util.StringUtils;

import com.nep.tsuki.file.processing.FileJobCode;

/**
 * 对象存储条件复制（CopyObject）执行结果
 *
 * @param code 执行结果状态码（SUCCESS / SOURCE_MISSING / SOURCE_CHANGED）
 * @param eTag 复制成功后服务端返回的目标对象 ETag；失败时为 {@code null}
 */
public record ObjectCopyResult(
        FileJobCode code,
        String eTag) {

    public ObjectCopyResult {
        Objects.requireNonNull(code, "code 不能为空");
        if (code.isSuccess() && !StringUtils.hasText(eTag)) {
            throw new IllegalArgumentException("复制成功结果必须包含目标对象 eTag");
        }
    }

    /**
     * 判断复制是否成功
     *
     * @return 成功返回 {@code true}
     */
    public boolean isSuccess() {
        return code.isSuccess();
    }

    /**
     * 构造复制成功结果
     *
     * @param eTag 目标对象 ETag
     * @return 复制成功结果对象
     */
    public static ObjectCopyResult success(String eTag) {
        return new ObjectCopyResult(FileJobCode.SUCCESS, eTag);
    }

    /**
     * 构造复制失败结果（源对象缺失或源 ETag 已变化）
     *
     * @param code 失败原因码（不可为 SUCCESS）
     * @return 复制失败结果对象
     */
    public static ObjectCopyResult failure(FileJobCode code) {
        Objects.requireNonNull(code, "code 不能为空");
        if (code.isSuccess()) {
            throw new IllegalArgumentException("失败结果不能使用 SUCCESS 状态码");
        }
        return new ObjectCopyResult(code, null);
    }
}

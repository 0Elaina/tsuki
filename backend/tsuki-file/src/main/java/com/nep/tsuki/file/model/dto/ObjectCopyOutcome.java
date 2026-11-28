package com.nep.tsuki.file.model.dto;

import java.util.Objects;

import com.nep.tsuki.file.processing.FileJobCode;

/**
 * 对象存储条件复制归档结果
 *
 * @param code 执行结果状态码（SUCCESS / SOURCE_MISSING / SOURCE_CHANGED）
 * @param eTag 复制成功后由服务端生成的目标对象 ETag（失败时为 null）
 */
public record ObjectCopyOutcome(
        FileJobCode code,
        String eTag) {

    public ObjectCopyOutcome {
        Objects.requireNonNull(code, "code 不能为空");
    }

    public boolean isSuccess() {
        return code.isSuccess();
    }

    public static ObjectCopyOutcome success(String eTag) {
        return new ObjectCopyOutcome(FileJobCode.SUCCESS, eTag);
    }

    public static ObjectCopyOutcome failure(FileJobCode code) {
        Objects.requireNonNull(code, "code 不能为空");
        if (code.isSuccess()) {
            throw new IllegalArgumentException("失败结果不能使用 SUCCESS 状态码");
        }
        return new ObjectCopyOutcome(code, null);
    }
}

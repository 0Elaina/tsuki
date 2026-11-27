package com.nep.tsuki.file.processing;

import java.util.Objects;

public record FileProcessResult<T>(
        FileJobCode code,
        T data) {

    public FileProcessResult {
        Objects.requireNonNull(code, "code 不能为空");
        if (code.isSuccess()) {
            Objects.requireNonNull(data, "成功结果 data 不能为空");
        }
    }

    public boolean isSuccess() {
        return code.isSuccess();
    }

    public static <T> FileProcessResult<T> success(T data) {
        return new FileProcessResult<>(FileJobCode.SUCCESS, data);
    }

    public static <T> FileProcessResult<T> failure(FileJobCode code) {
        Objects.requireNonNull(code, "code 不能为空");
        if (code.isSuccess()) {
            throw new IllegalArgumentException("失败结果不能使用 SUCCESS 状态码");
        }
        return new FileProcessResult<>(code, null);
    }
}

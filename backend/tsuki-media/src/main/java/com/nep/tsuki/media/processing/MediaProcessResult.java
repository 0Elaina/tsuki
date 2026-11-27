package com.nep.tsuki.media.processing;

import java.util.Objects;

public record MediaProcessResult<T>(
        MediaJobCode code,
        T data) {

    public MediaProcessResult {
        Objects.requireNonNull(code, "code 不能为空");
        if (code.isSuccess()) {
            Objects.requireNonNull(data, "成功结果 data 不能为空");
        }
    }

    public boolean isSuccess() {
        return code.isSuccess();
    }

    public static <T> MediaProcessResult<T> success(T data) {
        return new MediaProcessResult<>(MediaJobCode.SUCCESS, data);
    }

    public static <T> MediaProcessResult<T> failure(MediaJobCode code) {
        Objects.requireNonNull(code, "code 不能为空");
        if (code.isSuccess()) {
            throw new IllegalArgumentException("失败结果不能使用 SUCCESS 状态码");
        }
        return new MediaProcessResult<>(code, null);
    }
}

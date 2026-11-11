package com.nep.tsuki.api;

import java.util.Map;

public record ApiResponse<T>(
        T data,
        String code,
        String message,
        Map<String, String> errors) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, ApiCode.SUCCESS.code(), ApiCode.SUCCESS.message(), Map.of());
    }

    public static ApiResponse<Void> success() {
        return success(null);
    }

    public static ApiResponse<Void> error(ApiCode apiCode, String message) {
        return new ApiResponse<>(null, apiCode.code(), message, Map.of());
    }

    public static ApiResponse<Void> error(ApiCode apiCode) {
        return error(apiCode, apiCode.message());
    }
}

package com.nep.tsuki.api;

import org.springframework.http.HttpStatus;

public record ApiCode(
        HttpStatus httpStatus,
        String code,
        String message) {

    public static final ApiCode SUCCESS = new ApiCode(HttpStatus.OK, "SUCCESS", "响应成功");
    public static final ApiCode VALIDATION_FAILED = new ApiCode(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求参数校验失败");
    public static final ApiCode BAD_REQUEST = new ApiCode(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "请求格式不正确");
    public static final ApiCode NOT_FOUND = new ApiCode(HttpStatus.NOT_FOUND, "NOT_FOUND", "请求的资源不存在");
    public static final ApiCode METHOD_NOT_ALLOWED = new ApiCode(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "请求方法不受支持");
    public static final ApiCode UNSUPPORTED_MEDIA_TYPE = new ApiCode(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "请求内容类型不受支持");
    public static final ApiCode REQUEST_FAILED = new ApiCode(HttpStatus.BAD_REQUEST, "REQUEST_FAILED", "请求处理失败");
    public static final ApiCode INTERNAL_SERVER_ERROR = new ApiCode(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "服务器内部错误");
}

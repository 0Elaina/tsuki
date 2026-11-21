package com.nep.tsuki.common.api;

import org.springframework.http.HttpStatus;

public record ApiCode(
                HttpStatus httpStatus,
                String code,
                String message) {

        public static final ApiCode SUCCESS = new ApiCode(HttpStatus.OK, "SUCCESS", "响应成功");
        public static final ApiCode VALIDATION_FAILED = new ApiCode(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                        "请求参数校验失败");
        public static final ApiCode BAD_REQUEST = new ApiCode(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "请求格式不正确");
        public static final ApiCode NOT_FOUND = new ApiCode(HttpStatus.NOT_FOUND, "NOT_FOUND", "请求的资源不存在");
        public static final ApiCode METHOD_NOT_ALLOWED = new ApiCode(HttpStatus.METHOD_NOT_ALLOWED,
                        "METHOD_NOT_ALLOWED",
                        "请求方法不受支持");
        public static final ApiCode UNSUPPORTED_MEDIA_TYPE = new ApiCode(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "UNSUPPORTED_MEDIA_TYPE", "请求内容类型不受支持");
        public static final ApiCode REQUEST_FAILED = new ApiCode(HttpStatus.BAD_REQUEST, "REQUEST_FAILED", "请求处理失败");
        public static final ApiCode UNAUTHENTICATED = new ApiCode(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                        "未认证或凭据无效");
        public static final ApiCode PAYLOAD_TOO_LARGE = new ApiCode(HttpStatus.CONTENT_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                        "请求内容过大");
        public static final ApiCode INVALID_URL = new ApiCode(HttpStatus.BAD_REQUEST,
                        "INVALID_URL", "URL 格式无效");
        public static final ApiCode INVALID_COUNTRY_CODE = new ApiCode(HttpStatus.BAD_REQUEST,
                        "INVALID_COUNTRY_CODE", "国家或地区代码无效");
        public static final ApiCode ACCESS_DENIED = new ApiCode(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "无权限访问该资源");
        public static final ApiCode INTERNAL_SERVER_ERROR = new ApiCode(HttpStatus.INTERNAL_SERVER_ERROR,
                        "INTERNAL_SERVER_ERROR", "服务器内部错误");
}

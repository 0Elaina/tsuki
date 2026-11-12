package com.nep.tsuki.auth.constants;

import org.springframework.http.HttpStatus;

import com.nep.tsuki.api.ApiCode;

public final class AuthApiCode {
        private AuthApiCode() {
        }

        public static final ApiCode LOGIN_RATE_LIMITED = new ApiCode(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_RATE_LIMITED",
                        "登录请求过于频繁, 请稍候重试");
        public static final ApiCode LOGIN_UNAVAILABLE = new ApiCode(HttpStatus.SERVICE_UNAVAILABLE, "LOGIN_UNAVAILABLE",
                        "登录服务暂时不可用, 请稍后再试");
        public static final ApiCode INVALID_REFRESH_TOKEN = new ApiCode(HttpStatus.UNAUTHORIZED,
                        "INVALID_REFRESH_TOKEN", "刷新令牌无效或已过期");
}

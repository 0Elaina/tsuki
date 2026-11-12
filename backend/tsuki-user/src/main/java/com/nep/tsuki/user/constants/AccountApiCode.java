package com.nep.tsuki.user.constants;

import org.springframework.http.HttpStatus;

import com.nep.tsuki.api.ApiCode;

public class AccountApiCode {
    public static final ApiCode INVALID_CREDENTIALS = new ApiCode(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
            "非法的凭据");
    public static final ApiCode ACCOUNT_DISABLED = new ApiCode(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED",
            "账户已停用");
    public static final ApiCode ACCOUNT_NOT_FOUND = new ApiCode(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND",
            "账户不存在");
}

package com.nep.tsuki.exceptions;

import com.nep.tsuki.api.ApiCode;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final ApiCode apiCode;

    public BusinessException(ApiCode apiCode, String message) {
        super(message);
        this.apiCode = apiCode;
    }

    public BusinessException(ApiCode apiCode) {
        super(apiCode.message());
        this.apiCode = apiCode;
    }
}

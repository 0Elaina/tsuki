package com.nep.tsuki.tag.constants;

import org.springframework.http.HttpStatus;

import com.nep.tsuki.common.api.ApiCode;

public final class TagApiCode {
    private TagApiCode() {
    }

    public static final ApiCode TAG_NAME_CONFLICT = new ApiCode(HttpStatus.CONFLICT, "TAG_NAME_CONFLICT",
            "标签名称已存在");
    public static final ApiCode TAG_NOT_FOUND = new ApiCode(HttpStatus.NOT_FOUND, "TAG_NOT_FOUND",
            "标签不存在");
}

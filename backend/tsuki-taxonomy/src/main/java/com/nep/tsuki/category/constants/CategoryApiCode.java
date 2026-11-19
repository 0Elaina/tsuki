package com.nep.tsuki.category.constants;

import org.springframework.http.HttpStatus;

import com.nep.tsuki.common.api.ApiCode;

public final class CategoryApiCode {
    private CategoryApiCode() {
    }

    public static final ApiCode CATEGORY_NAME_CONFLICT = new ApiCode(HttpStatus.CONFLICT, "CATEGORY_NAME_CONFLICT",
            "分类名称已存在");
    public static final ApiCode CATEGORY_NOT_FOUND = new ApiCode(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND",
            "分类不存在");
}

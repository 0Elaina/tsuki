package com.nep.tsuki.media.constants;

import org.springframework.http.HttpStatus;

import com.nep.tsuki.common.api.ApiCode;

public final class MediaApiCode {
    private MediaApiCode() {
    }

    public static final ApiCode INVALID_IMAGE_FILE = new ApiCode(HttpStatus.BAD_REQUEST, "INVALID_IMAGE_FILE",
            "图片文件已损坏或尺寸超出限制");

    public static final ApiCode MEDIA_STORAGE_UNAVAILABLE = new ApiCode(HttpStatus.SERVICE_UNAVAILABLE,
            "MEDIA_STORAGE_UNAVAILABLE", "媒体存储服务暂时不可用，请稍后再试");

    public static final ApiCode MEDIA_ASSET_NOT_BINDABLE = new ApiCode(HttpStatus.CONFLICT, "MEDIA_ASSET_NOT_BINDABLE",
            "媒体资源不存在、已过期或已被绑定");
}

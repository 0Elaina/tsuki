package com.nep.tsuki.media.image.model.enums;

import org.springframework.http.MediaType;

import com.nep.tsuki.common.api.ApiCode;
import com.nep.tsuki.common.exceptions.BusinessException;
import com.nep.tsuki.media.constants.MediaConstants;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ImageType {
    JPG("jpg", MediaType.IMAGE_JPEG_VALUE),
    JPEG("jpeg", MediaType.IMAGE_JPEG_VALUE),
    PNG("png", MediaType.IMAGE_PNG_VALUE),
    WEBP("webp", MediaConstants.IMAGE_WEBP_VALUE);

    private final String lowerName;
    private final String mediaType;

    public static ImageType fromLowerName(String lowerName) {
        for (ImageType type : values()) {
            if (type.lowerName.equals(lowerName)) {
                return type;
            }
        }
        throw new BusinessException(ApiCode.UNSUPPORTED_MEDIA_TYPE, "不支持的图片格式");
    }
}

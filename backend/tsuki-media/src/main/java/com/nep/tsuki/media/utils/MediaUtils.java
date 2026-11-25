package com.nep.tsuki.media.utils;

import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

public final class MediaUtils {
    private MediaUtils() {
    }

    /**
     * 判断实际的 Content-Type 与预期的 MIME 主类型及子类型是否一致
     *
     * @param expectedContentType 预期的 Content-Type 字符串
     * @param actualContentType   实际的 Content-Type 字符串
     * @return 类型与子类型一致返回 {@code true}，若实际类型为空、格式非法或不匹配则返回 {@code false}
     */
    public static boolean isSameMediaType(String expectedContentType, String actualContentType) {
        // 实际类型为空白字符串时直接判定为不匹配
        if (!StringUtils.hasText(actualContentType)) {
            return false;
        }
        MediaType expectedMediaType = MediaType.parseMediaType(expectedContentType);
        try {
            // 解析实际媒体类型并仅比对 type/subtype（忽略 charset 等附加参数）
            MediaType actualMediaType = MediaType.parseMediaType(actualContentType.strip());
            return expectedMediaType.equalsTypeAndSubtype(actualMediaType);
        } catch (InvalidMediaTypeException ex) {
            // 外部传入的媒体类型格式非法时视为不匹配
            return false;
        }
    }
}

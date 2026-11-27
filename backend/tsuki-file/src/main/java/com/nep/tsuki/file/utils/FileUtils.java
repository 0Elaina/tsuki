package com.nep.tsuki.file.utils;

import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

import com.nep.tsuki.common.api.ApiCode;
import com.nep.tsuki.common.exceptions.BusinessException;

public final class FileUtils {
    public static final int MAX_ORIGINAL_FILENAME_LENGTH = 255;

    private FileUtils() {
    }

    /**
     * 判断实际的 Content-Type 与预期的 MIME 主类型及子类型是否一致
     *
     * @param expectedContentType 预期的 Content-Type 字符串
     * @param actualContentType   实际的 Content-Type 字符串
     * @return 类型与子类型一致返回 {@code true}，若实际类型为空、格式非法或不匹配则返回 {@code false}
     */
    public static boolean isSameMediaType(String expectedContentType, String actualContentType) {
        if (!StringUtils.hasText(actualContentType)) {
            return false;
        }
        MediaType expectedMediaType = MediaType.parseMediaType(expectedContentType);
        try {
            MediaType actualMediaType = MediaType.parseMediaType(actualContentType.strip());
            return expectedMediaType.equalsTypeAndSubtype(actualMediaType);
        } catch (InvalidMediaTypeException ex) {
            return false;
        }
    }

    /**
     * 规范化并校验原始文件名：剥离目录前缀、拦截控制字符并校验长度上限；为空白或仅含目录占位符时返回 null
     *
     * @param originalFilename 调用方传入的原始文件名（可为 null）
     * @return 规范化后的文件名，无有效内容时返回 {@code null}
     */
    public static String normalizeOriginalFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return null;
        }
        String stripped = originalFilename.strip();
        int lastSeparator = Math.max(stripped.lastIndexOf('/'), stripped.lastIndexOf('\\'));
        String baseName = (lastSeparator >= 0 ? stripped.substring(lastSeparator + 1) : stripped).strip();
        if (!StringUtils.hasText(baseName) || ".".equals(baseName) || "..".equals(baseName)) {
            return null;
        }
        for (int i = 0; i < baseName.length(); i++) {
            char ch = baseName.charAt(i);
            if (ch < 0x20 || ch == 0x7F) {
                throw new BusinessException(ApiCode.VALIDATION_FAILED, "原始文件名包含非法控制字符");
            }
        }
        if (baseName.length() > MAX_ORIGINAL_FILENAME_LENGTH) {
            throw new BusinessException(ApiCode.VALIDATION_FAILED,
                    "原始文件名长度不能超过 " + MAX_ORIGINAL_FILENAME_LENGTH + " 个字符");
        }
        return baseName;
    }
}

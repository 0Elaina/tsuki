package com.nep.tsuki.file.model.enums;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

import org.springframework.util.StringUtils;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文件服务集中维护的具名图片派生预设（按规格编码与修订版本注册）
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum ImageVariantPreset {

    /** 安全可交付的原图级预设第 1 版（不缩放，执行 EXIF 正向校正、剥离隐私元数据并按高质量安全重编码） */
    FULL("FULL", 1, null, 0.92f);

    /** 规格编码 */
    private final String code;
    /** 预设修订版本号 */
    private final int revision;
    /** 长边最大像素限制；为 null 时保持原图尺寸不缩放 */
    private final Integer maxEdgePx;
    /** JPEG 压缩质量 */
    private final float jpegQuality;

    /**
     * 按规格编码与精确修订版本查找已注册预设
     */
    public static Optional<ImageVariantPreset> find(String code, int revision) {
        if (!StringUtils.hasText(code) || revision <= 0) {
            return Optional.empty();
        }
        String normalizedCode = code.strip();
        return Arrays.stream(values())
                .filter(preset -> preset.code.equals(normalizedCode) && preset.revision == revision)
                .findFirst();
    }

    /**
     * 按规格编码查找最新修订版本的预设
     */
    public static Optional<ImageVariantPreset> findLatest(String code) {
        if (!StringUtils.hasText(code)) {
            return Optional.empty();
        }
        String normalizedCode = code.strip();
        return Arrays.stream(values())
                .filter(preset -> preset.code.equals(normalizedCode))
                .max(Comparator.comparingInt(ImageVariantPreset::getRevision));
    }
}

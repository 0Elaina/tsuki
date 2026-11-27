package com.nep.tsuki.file.model.enums;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文件服务集中维护的具名图片派生预设（按文件类型拆分，避免跨文件类型字段污染）
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum ImageVariantPreset {

    /** 安全可交付的原图级预设（不缩放，仅执行 EXIF 正向校正、剥离隐私元数据并按高质量安全重编码） */
    FULL("full", null, 0.92f);

    private final String fileStem;
    /** 长边最大像素限制；为 null 时保持原图尺寸不缩放 */
    private final Integer maxEdgePx;
    private final float jpegQuality;
}

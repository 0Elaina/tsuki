package com.nep.tsuki.file.processing.image;

import java.util.Objects;

import com.nep.tsuki.file.model.enums.ImageContentType;

public record ImageProcessingResult(
        ImageContentType contentType,
        byte[] content,
        int width,
        int height) {

    public ImageProcessingResult {
        Objects.requireNonNull(contentType, "contentType 不能为空");
        Objects.requireNonNull(content, "content 不能为空");
        if (content.length == 0) {
            throw new IllegalArgumentException("content 不能为空数组");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("输出图像素宽高必须大于 0");
        }
    }
}

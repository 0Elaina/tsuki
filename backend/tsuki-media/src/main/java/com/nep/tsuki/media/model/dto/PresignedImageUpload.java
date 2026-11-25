package com.nep.tsuki.media.model.dto;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 图片预签名上传凭证
 *
 * @param url       用于直传的预签名目标地址
 * @param expiresAt 上传凭证的过期时间
 * @param headers   上传请求必须携带的 HTTP 请求头
 */
public record PresignedImageUpload(
        URI url,
        Instant expiresAt,
        Map<String, String> headers) {

    public PresignedImageUpload {
        Objects.requireNonNull(url, "url 不能为空");
        Objects.requireNonNull(expiresAt, "expiresAt 不能为空");
        Objects.requireNonNull(headers, "headers 不能为空");
        headers = Map.copyOf(headers);
    }

    /**
     * 隐藏敏感的签名 URL 与请求头，避免在日志中泄露临时上传凭证
     */
    @Override
    public String toString() {
        return "PresignedImageUpload[expiresAt=" + expiresAt + "]";
    }
}

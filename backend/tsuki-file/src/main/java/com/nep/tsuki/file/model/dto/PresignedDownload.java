package com.nep.tsuki.file.model.dto;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/**
 * 私有文件访问预签名下载凭证
 *
 * @param url       预签名 GET 访问地址
 * @param expiresAt 预签名过期时间
 */
public record PresignedDownload(
        URI url,
        Instant expiresAt) {

    public PresignedDownload {
        Objects.requireNonNull(url, "url 不能为空");
        Objects.requireNonNull(expiresAt, "expiresAt 不能为空");
    }

    @Override
    public String toString() {
        return "PresignedDownload[expiresAt=" + expiresAt + "]";
    }
}

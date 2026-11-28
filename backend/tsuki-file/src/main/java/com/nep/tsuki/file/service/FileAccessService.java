package com.nep.tsuki.file.service;

import java.net.URI;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.nep.tsuki.file.config.FileAccessProperties;
import com.nep.tsuki.file.model.enums.FileStorageScope;
import com.nep.tsuki.file.storage.FileObjectStorage;
import com.nep.tsuki.infra.storage.s3.S3Properties;

import lombok.RequiredArgsConstructor;

/**
 * 文件访问地址解析服务：私有范围签发临时 GET 预签名 URL，公开范围拼接交付根地址
 */
@Service
@RequiredArgsConstructor
public class FileAccessService {
    private final FileObjectStorage fileObjectStorage;
    private final FileAccessProperties fileAccessProperties;
    private final S3Properties s3Properties;

    /**
     * 按逻辑存储范围与对象键解析可访问的 URI
     */
    public URI resolveUrl(FileStorageScope scope, String objectKey) {
        Objects.requireNonNull(scope, "scope 不能为空");
        if (!StringUtils.hasText(objectKey)) {
            throw new IllegalArgumentException("objectKey 不能为空");
        }
        String normalizedKey = objectKey.strip();

        return switch (scope) {
            case PRIVATE -> fileObjectStorage.presignGet(
                    FileStorageScope.PRIVATE,
                    normalizedKey,
                    fileAccessProperties.getPresignedGetTtl());
            case DELIVERY -> {
                URI baseUri = s3Properties.resolveDeliveryBaseUri()
                        .orElseThrow(() -> new IllegalStateException("公开交付地址（tsuki.s3.delivery-base-url）未配置"));
                String relativeKey = normalizedKey.startsWith("/") ? normalizedKey.substring(1) : normalizedKey;
                yield URI.create(baseUri.toASCIIString() + "/" + relativeKey);
            }
        };
    }

    /**
     * 校验目标逻辑存储范围已配置可写存储桶（用于申请公开衍生物前快速失败）
     */
    public void ensureScopeWritable(FileStorageScope scope) {
        Objects.requireNonNull(scope, "scope 不能为空");
        if (scope == FileStorageScope.DELIVERY && !StringUtils.hasText(s3Properties.getDeliveryBucket())) {
            throw new IllegalStateException("公开交付存储桶（tsuki.s3.delivery-bucket）未配置");
        }
    }
}

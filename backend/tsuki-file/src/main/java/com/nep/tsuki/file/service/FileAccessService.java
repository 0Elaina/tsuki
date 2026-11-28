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
 * 文件访问地址统一解析服务：私有资源签发默认 5 分钟的 GET 预签名地址，公开交付资源拼接配置的交付根地址
 */
@Service
@RequiredArgsConstructor
public class FileAccessService {
    private final FileObjectStorage fileObjectStorage;
    private final FileAccessProperties fileAccessProperties;
    private final S3Properties s3Properties;

    /**
     * 按逻辑存储范围与对象键解析可供客户端访问的 URI
     *
     * @param scope     逻辑存储范围（PRIVATE / DELIVERY）
     * @param objectKey 完整 S3 对象键
     * @return 解析后的访问地址 URI
     */
    public URI resolveUrl(FileStorageScope scope, String objectKey) {
        Objects.requireNonNull(scope, "scope 不能为空");
        if (!StringUtils.hasText(objectKey)) {
            throw new IllegalArgumentException("objectKey 不能为空");
        }
        String normalizedKey = objectKey.strip();

        return switch (scope) {
            // 私有范围签发受有效期约束的临时 GET 预签名地址
            case PRIVATE -> fileObjectStorage.presignGet(
                    FileStorageScope.PRIVATE,
                    normalizedKey,
                    fileAccessProperties.getPresignedGetTtl());
            // 公开交付范围直接拼接已规范化的交付根地址与相对对象键
            case DELIVERY -> {
                URI baseUri = s3Properties.resolveDeliveryBaseUri()
                        .orElseThrow(() -> new IllegalStateException("公开交付地址（tsuki.s3.delivery-base-url）未配置"));
                String relativeKey = normalizedKey.startsWith("/") ? normalizedKey.substring(1) : normalizedKey;
                yield URI.create(baseUri.toASCIIString() + "/" + relativeKey);
            }
        };
    }

    /**
     * 校验目标逻辑存储范围已配置可写存储桶（用于在申请公开衍生物前快速失败）
     *
     * @param scope 目标逻辑存储范围
     */
    public void ensureScopeWritable(FileStorageScope scope) {
        Objects.requireNonNull(scope, "scope 不能为空");
        if (scope == FileStorageScope.DELIVERY && !StringUtils.hasText(s3Properties.getDeliveryBucket())) {
            throw new IllegalStateException("公开交付存储桶（tsuki.s3.delivery-bucket）未配置");
        }
    }
}

package com.nep.tsuki.file.model.dto;

import java.util.Objects;

import org.springframework.util.StringUtils;

import com.nep.tsuki.file.model.enums.FileStorageScope;

/**
 * 对象存储逻辑定位引用（逻辑桶范围 + 完整对象键）
 *
 * @param scope     逻辑存储范围
 * @param objectKey 完整 S3 对象键
 */
public record StorageObjectRef(
        FileStorageScope scope,
        String objectKey) {

    public StorageObjectRef {
        Objects.requireNonNull(scope, "scope 不能为空");
        if (!StringUtils.hasText(objectKey)) {
            throw new IllegalArgumentException("objectKey 不能为空");
        }
        objectKey = objectKey.strip();
    }
}

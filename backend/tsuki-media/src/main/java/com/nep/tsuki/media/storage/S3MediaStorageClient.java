package com.nep.tsuki.media.storage;

import org.springframework.stereotype.Component;

import com.nep.tsuki.common.exceptions.BusinessException;
import com.nep.tsuki.media.config.PublicMediaStorageProperties;
import com.nep.tsuki.media.constants.MediaApiCode;
import com.nep.tsuki.media.model.enums.MediaStorageSpace;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3MediaStorageClient {
    private final S3Client s3Client;
    private final PublicMediaStorageProperties publicMediaStorageProperties;

    /**
     * 使用对应存储空间的默认缓存策略上传媒体对象
     *
     * @param storageSpace 媒体存储空间
     * @param objectKey    媒体对象键
     * @param bytes        媒体对象字节数组
     * @param contentType  媒体对象内容类型
     */
    public void putObject(MediaStorageSpace storageSpace, String objectKey, byte[] bytes, String contentType) {
        putObject(storageSpace, objectKey, bytes, contentType, resolveCacheControl(storageSpace));
    }

    /**
     * 上传媒体对象
     *
     * @param storageSpace 媒体存储空间
     * @param objectKey    媒体对象键
     * @param bytes        媒体对象字节数组
     * @param contentType  媒体对象内容类型
     * @param cacheControl 媒体对象缓存控制
     */
    public void putObject(MediaStorageSpace storageSpace, String objectKey, byte[] bytes, String contentType,
            String cacheControl) {
        String bucket = resolveBucket(storageSpace);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .cacheControl(cacheControl)
                .contentLength((long) bytes.length)
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromBytes(bytes));
        } catch (SdkException ex) {
            log.error("上传媒体对象失败: bucket={}, objectKey={}", bucket, objectKey, ex);
            throw new BusinessException(MediaApiCode.MEDIA_STORAGE_UNAVAILABLE);
        }
    }

    /**
     * 构建公开媒体对象 URL
     *
     * @param storageSpace    媒体存储空间
     * @param pathOrObjectKey 媒体对象路径或对象键
     * @return 公开媒体对象 URL
     */
    public String buildPublicUrl(MediaStorageSpace storageSpace, String pathOrObjectKey) {
        String baseUrl = resolvePublicBaseUrl(storageSpace);
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String trimmedPath = pathOrObjectKey.strip();
        String normalizedPath = trimmedPath.startsWith("/") ? trimmedPath : "/" + trimmedPath;
        return baseUrl + normalizedPath;
    }

    private String resolveBucket(MediaStorageSpace storageSpace) {
        return switch (storageSpace) {
            case PUBLIC_MEDIA -> publicMediaStorageProperties.getBucket();
        };
    }

    private String resolvePublicBaseUrl(MediaStorageSpace storageSpace) {
        return switch (storageSpace) {
            case PUBLIC_MEDIA -> publicMediaStorageProperties.getPublicBaseUrl();
        };
    }

    private String resolveCacheControl(MediaStorageSpace storageSpace) {
        return switch (storageSpace) {
            case PUBLIC_MEDIA -> publicMediaStorageProperties.getCacheControl();
        };
    }
}

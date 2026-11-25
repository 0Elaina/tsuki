package com.nep.tsuki.media.storage.s3;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

import com.nep.tsuki.infra.storage.s3.S3Properties;
import com.nep.tsuki.media.config.MediaUploadProperties;
import com.nep.tsuki.media.model.dto.PresignedImageUpload;
import com.nep.tsuki.media.model.dto.StagedObjectMetadata;
import com.nep.tsuki.media.model.dto.StagingObjectKey;
import com.nep.tsuki.media.model.enums.ImageContentType;
import com.nep.tsuki.media.model.enums.MediaKind;
import com.nep.tsuki.media.storage.StagedUploadStorage;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

public class S3StagedUploadStorage implements StagedUploadStorage {
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;
    private final MediaUploadProperties mediaUploadProperties;

    public S3StagedUploadStorage(
            S3Client s3Client,
            S3Presigner s3Presigner,
            S3Properties s3Properties,
            MediaUploadProperties mediaUploadProperties) {
        Objects.requireNonNull(s3Client, "s3Client 不能为空");
        Objects.requireNonNull(s3Presigner, "s3Presigner 不能为空");
        Objects.requireNonNull(s3Properties, "s3Properties 不能为空");
        Objects.requireNonNull(mediaUploadProperties, "mediaUploadProperties 不能为空");
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.s3Properties = s3Properties;
        this.mediaUploadProperties = mediaUploadProperties;
    }

    /**
     * 生成图片直传的预签名凭证（HTTP PUT）
     *
     * @param key         暂存对象键
     * @param contentType 图片媒体类型（MIME Type）
     * @param byteSize    预期上传的文件字节大小
     * @return 预签名上传凭证（包含上传地址、过期时间及必需请求头）
     */
    @Override
    public PresignedImageUpload presignImagePut(StagingObjectKey key, String contentType, long byteSize) {
        // 校验入参合法性（非空、图片类型白名单、文件大小限制）
        Objects.requireNonNull(key, "key 不能为空");
        if (key.kind() != MediaKind.IMAGE) {
            throw new IllegalArgumentException("暂存对象类型必须为 IMAGE: " + key.kind());
        }
        Objects.requireNonNull(contentType, "contentType 不能为空");
        if (!ImageContentType.isSupported(contentType)) {
            throw new IllegalArgumentException("不支持的图片 Content-Type: " + contentType);
        }
        long maxImageBytes = mediaUploadProperties.getMaxImageBytes();
        if (byteSize < 1L || byteSize > maxImageBytes) {
            throw new IllegalArgumentException("图片字节数超出允许范围 [1, " + maxImageBytes + "]:" + byteSize);
        }

        // 构建 S3 上传请求，强制绑定长度与类型并禁止覆盖同名对象（ifNoneMatch="*"）
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(key.value())
                .contentType(contentType)
                .contentLength(byteSize)
                .ifNoneMatch("*")
                .build();

        // 配置预签名请求及凭证有效期
        PutObjectPresignRequest putObjectPresignRequest = PutObjectPresignRequest.builder()
                .putObjectRequest(putObjectRequest)
                .signatureDuration(mediaUploadProperties.getSignatureTtl())
                .build();

        // 调用 S3 SDK 生成预签名凭证
        PresignedPutObjectRequest presignedPutObjectRequest;
        try {
            presignedPutObjectRequest = s3Presigner.presignPutObject(putObjectPresignRequest);
        } catch (SdkException ex) {
            throw new IllegalStateException("生成图片预签名 PUT 请求失败", ex);
        }

        // 转换上传地址为标准 URI
        URI uri;
        try {
            uri = presignedPutObjectRequest.url().toURI();
        } catch (URISyntaxException ex) {
            throw new IllegalStateException("预签名 URL 转换 URI 失败", ex);
        }

        // 提取参与签名的必需请求头（客户端直传时必须原样携带，否则校验失败）
        Map<String, List<String>> signedHeaders = presignedPutObjectRequest.signedHeaders();
        String contentTypeHeader = extractSingleHeader(
                signedHeaders,
                HttpHeaders.CONTENT_TYPE.toLowerCase(Locale.ROOT));
        String ifNoneMatchHeader = extractSingleHeader(
                signedHeaders,
                HttpHeaders.IF_NONE_MATCH.toLowerCase(Locale.ROOT));
        Map<String, String> headers = Map.of(
                HttpHeaders.CONTENT_TYPE, contentTypeHeader,
                HttpHeaders.IF_NONE_MATCH, ifNoneMatchHeader);

        // 封装并返回最终凭证
        return new PresignedImageUpload(uri, presignedPutObjectRequest.expiration(), headers);
    }

    /**
     * 查询暂存对象的元数据
     *
     * @param objectKey 数据库保存的完整暂存对象键
     * @return 若对象存在则返回其元数据，否则返回 {@link Optional#empty()}
     */
    @Override
    public Optional<StagedObjectMetadata> find(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            throw new IllegalArgumentException("objectKey 不能为空");
        }

        // 构建 S3 HEAD 请求（仅获取元数据，不下载文件内容）
        HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(objectKey)
                .build();

        try {
            // 调用 S3 接口获取元数据并校验文件大小合法性
            HeadObjectResponse response = s3Client.headObject(headObjectRequest);
            Long contentLength = response.contentLength();
            if (contentLength == null || contentLength < 0) {
                throw new IllegalStateException("对象存储返回的 Content-Length 无效: " + contentLength);
            }
            return Optional.of(new StagedObjectMetadata(contentLength, response.contentType(), response.eTag()));
        } catch (NoSuchKeyException ex) {
            // 对象不存在，返回空 Optional
            return Optional.empty();
        } catch (S3Exception ex) {
            // 处理 S3 服务端异常（兼容 404 不存在场景，区分权限拒绝与其他服务端错误）
            switch (ex.statusCode()) {
                case 404:
                    return Optional.empty();
                case 403:
                    throw new IllegalStateException("访问暂存对象被拒绝 (HTTP 403)", ex);
                default:
                    throw new IllegalStateException("查询暂存对象元数据失败 (HTTP " + ex.statusCode() + ")", ex);
            }
        } catch (SdkException ex) {
            // 处理网络超时或客户端 SDK 异常
            throw new IllegalStateException("查询暂存对象元数据失败", ex);
        }
    }

    private String extractSingleHeader(Map<String, List<String>> signedHeaders, String headerName) {
        List<String> values = signedHeaders.get(headerName);
        if (values == null || values.size() != 1 || !StringUtils.hasText(values.getFirst())) {
            throw new IllegalStateException("预签名请求头缺失或非单值: " + headerName);
        }
        return values.getFirst();
    }
}

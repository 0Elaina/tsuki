package com.nep.tsuki.file.storage.s3;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

import com.nep.tsuki.file.config.FileUploadProperties;
import com.nep.tsuki.file.constants.FileStorageConstants;
import com.nep.tsuki.file.model.dto.PresignedUpload;
import com.nep.tsuki.file.model.dto.StagedObjectMetadata;
import com.nep.tsuki.file.storage.StagedUploadStorage;
import com.nep.tsuki.infra.storage.s3.S3Properties;

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
    private final FileUploadProperties fileUploadProperties;

    public S3StagedUploadStorage(
            S3Client s3Client,
            S3Presigner s3Presigner,
            S3Properties s3Properties,
            FileUploadProperties fileUploadProperties) {
        Objects.requireNonNull(s3Client, "s3Client 不能为空");
        Objects.requireNonNull(s3Presigner, "s3Presigner 不能为空");
        Objects.requireNonNull(s3Properties, "s3Properties 不能为空");
        Objects.requireNonNull(fileUploadProperties, "fileUploadProperties 不能为空");
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.s3Properties = s3Properties;
        this.fileUploadProperties = fileUploadProperties;
    }

    /**
     * 生成文件直传的预签名凭证（HTTP PUT）
     *
     * @param key         暂存对象键
     * @param contentType 文件媒体类型（MIME Type）
     * @param byteSize    预期上传的文件字节大小
     * @param maxBytes    该文件类型允许的最大字节数上限
     * @return 预签名上传凭证（包含上传地址、过期时间及必需请求头）
     */
    @Override
    public PresignedUpload presignPut(String stagingObjectKey, String contentType, long byteSize, long maxBytes) {
        if (!StringUtils.hasText(stagingObjectKey)
                || !stagingObjectKey.startsWith(FileStorageConstants.STAGING_ROOT_PREFIX + "/")) {
            throw new IllegalArgumentException("非法的暂存对象键: " + stagingObjectKey);
        }
        if (!StringUtils.hasText(contentType)) {
            throw new IllegalArgumentException("contentType 不能为空");
        }
        if (maxBytes <= 0L) {
            throw new IllegalArgumentException("maxBytes 必须大于 0: " + maxBytes);
        }
        if (byteSize < 1L || byteSize > maxBytes) {
            throw new IllegalArgumentException("文件字节数超出允许范围 [1, " + maxBytes + "]: " + byteSize);
        }

        // 构建 S3 上传请求，强制绑定长度与类型并禁止覆盖同名对象（ifNoneMatch="*"）
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.getPrivateBucket())
                .key(stagingObjectKey)
                .contentType(contentType.strip())
                .contentLength(byteSize)
                .ifNoneMatch("*")
                .build();

        // 配置预签名请求及凭证有效期
        PutObjectPresignRequest putObjectPresignRequest = PutObjectPresignRequest.builder()
                .putObjectRequest(putObjectRequest)
                .signatureDuration(fileUploadProperties.getSignatureTtl())
                .build();

        // 调用 S3 SDK 生成预签名凭证
        PresignedPutObjectRequest presignedPutObjectRequest;
        try {
            presignedPutObjectRequest = s3Presigner.presignPutObject(putObjectPresignRequest);
        } catch (SdkException ex) {
            throw new IllegalStateException("生成文件预签名 PUT 请求失败", ex);
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

        return new PresignedUpload(uri, presignedPutObjectRequest.expiration(), headers);
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

        HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                .bucket(s3Properties.getPrivateBucket())
                .key(objectKey)
                .build();

        try {
            HeadObjectResponse response = s3Client.headObject(headObjectRequest);
            Long contentLength = response.contentLength();
            if (contentLength == null || contentLength < 0) {
                throw new IllegalStateException("对象存储返回的 Content-Length 无效: " + contentLength);
            }
            return Optional.of(new StagedObjectMetadata(contentLength, response.contentType(), response.eTag()));
        } catch (NoSuchKeyException ex) {
            return Optional.empty();
        } catch (S3Exception ex) {
            switch (ex.statusCode()) {
                case 404:
                    return Optional.empty();
                case 403:
                    throw new IllegalStateException("访问暂存对象被拒绝 (HTTP 403)", ex);
                default:
                    throw new IllegalStateException("查询暂存对象元数据失败 (HTTP " + ex.statusCode() + ")", ex);
            }
        } catch (SdkException ex) {
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

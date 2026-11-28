package com.nep.tsuki.file.storage.s3;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.util.StringUtils;

import com.nep.tsuki.file.model.dto.ObjectCopyOutcome;
import com.nep.tsuki.file.model.dto.PresignedDownload;
import com.nep.tsuki.file.model.dto.StorageObjectRef;
import com.nep.tsuki.file.model.enums.FileStorageScope;
import com.nep.tsuki.file.processing.FileJobCode;
import com.nep.tsuki.file.storage.FileObjectStorage;
import com.nep.tsuki.infra.storage.s3.S3Properties;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.CopyObjectResult;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@Slf4j
public class S3FileObjectStorage implements FileObjectStorage {
    private static final int COPY_BUFFER_SIZE = 8192;
    private static final int DELETE_BATCH_SIZE = 1000;
    private static final String ERROR_CODE_NO_SUCH_KEY = "NoSuchKey";

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    public S3FileObjectStorage(S3Client s3Client, S3Presigner s3Presigner, S3Properties s3Properties) {
        this.s3Client = Objects.requireNonNull(s3Client, "s3Client 不能为空");
        this.s3Presigner = Objects.requireNonNull(s3Presigner, "s3Presigner 不能为空");
        this.s3Properties = Objects.requireNonNull(s3Properties, "s3Properties 不能为空");
    }

    @Override
    public FileJobCode downloadToFile(
            FileStorageScope scope,
            String objectKey,
            String expectedEtag,
            long expectedByteSize,
            Path targetFile) {
        Objects.requireNonNull(scope, "scope 不能为空");
        if (!StringUtils.hasText(objectKey)) {
            throw new IllegalArgumentException("objectKey 不能为空");
        }
        if (expectedByteSize <= 0L) {
            throw new IllegalArgumentException("expectedByteSize 必须大于 0");
        }
        Objects.requireNonNull(targetFile, "targetFile 不能为空");

        String bucket = resolveBucket(scope);
        GetObjectRequest.Builder requestBuilder = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey);
        if (StringUtils.hasText(expectedEtag)) {
            requestBuilder.ifMatch(expectedEtag);
        }

        try (
                ResponseInputStream<GetObjectResponse> inputStream = s3Client.getObject(requestBuilder.build());
                OutputStream outputStream = Files.newOutputStream(targetFile)) {

            Long responseLength = inputStream.response().contentLength();
            if (responseLength != null && responseLength.longValue() != expectedByteSize) {
                log.warn("源对象响应长度与记录不符: bucket={}, objectKey={}, expected={}, actual={}",
                        bucket, objectKey, expectedByteSize, responseLength);
                return FileJobCode.SOURCE_CHANGED;
            }

            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            long totalBytesRead = 0L;
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                totalBytesRead += bytesRead;
                if (totalBytesRead > expectedByteSize) {
                    log.warn("源对象实际读取字节超过预期: bucket={}, objectKey={}, expected={}",
                            bucket, objectKey, expectedByteSize);
                    return FileJobCode.SOURCE_CHANGED;
                }
                outputStream.write(buffer, 0, bytesRead);
            }

            if (totalBytesRead != expectedByteSize) {
                log.warn("源对象实际读取字节不足预期: bucket={}, objectKey={}, expected={}, actual={}",
                        bucket, objectKey, expectedByteSize, totalBytesRead);
                return FileJobCode.SOURCE_CHANGED;
            }

            return FileJobCode.SUCCESS;
        } catch (NoSuchKeyException ex) {
            return FileJobCode.SOURCE_MISSING;
        } catch (S3Exception ex) {
            return switch (ex.statusCode()) {
                case 404 -> FileJobCode.SOURCE_MISSING;
                case 412 -> FileJobCode.SOURCE_CHANGED;
                default -> throw new IllegalStateException(
                        "读取文件对象失败 (HTTP " + ex.statusCode() + "): bucket=" + bucket + ", objectKey=" + objectKey,
                        ex);
            };
        } catch (SdkException | IOException ex) {
            throw new IllegalStateException("读取文件对象失败: bucket=" + bucket + ", objectKey=" + objectKey, ex);
        }
    }

    @Override
    public ObjectCopyOutcome copyObjectIfMatch(
            FileStorageScope sourceScope,
            String sourceKey,
            String sourceEtag,
            FileStorageScope targetScope,
            String targetKey) {
        Objects.requireNonNull(sourceScope, "sourceScope 不能为空");
        Objects.requireNonNull(targetScope, "targetScope 不能为空");
        if (!StringUtils.hasText(sourceKey)) {
            throw new IllegalArgumentException("sourceKey 不能为空");
        }
        if (!StringUtils.hasText(targetKey)) {
            throw new IllegalArgumentException("targetKey 不能为空");
        }

        String sourceBucket = resolveBucket(sourceScope);
        String targetBucket = resolveBucket(targetScope);
        CopyObjectRequest.Builder builder = CopyObjectRequest.builder()
                .sourceBucket(sourceBucket)
                .sourceKey(sourceKey)
                .destinationBucket(targetBucket)
                .destinationKey(targetKey);
        if (StringUtils.hasText(sourceEtag)) {
            builder.copySourceIfMatch(sourceEtag);
        }

        try {
            CopyObjectResponse response = s3Client.copyObject(builder.build());
            CopyObjectResult result = response.copyObjectResult();
            String targetEtag = result != null ? result.eTag() : null;
            return ObjectCopyOutcome.success(targetEtag);
        } catch (NoSuchKeyException ex) {
            return ObjectCopyOutcome.failure(FileJobCode.SOURCE_MISSING);
        } catch (S3Exception ex) {
            return switch (ex.statusCode()) {
                case 404 -> ObjectCopyOutcome.failure(FileJobCode.SOURCE_MISSING);
                case 412 -> ObjectCopyOutcome.failure(FileJobCode.SOURCE_CHANGED);
                default -> throw new IllegalStateException(
                        "复制归档对象失败 (HTTP " + ex.statusCode() + "): sourceKey=" + sourceKey + ", targetKey=" + targetKey,
                        ex);
            };
        } catch (SdkException ex) {
            throw new IllegalStateException(
                    "复制归档对象失败: sourceKey=" + sourceKey + ", targetKey=" + targetKey, ex);
        }
    }

    @Override
    public String archiveObject(String sourceKey, String sourceEtag, String targetKey, Path localFile,
            String contentType, long byteSize) {
        if (!StringUtils.hasText(sourceKey)) {
            throw new IllegalArgumentException("sourceKey 不能为空");
        }
        if (!StringUtils.hasText(targetKey)) {
            throw new IllegalArgumentException("targetKey 不能为空");
        }
        Objects.requireNonNull(localFile, "localFile 不能为空");
        if (!StringUtils.hasText(contentType)) {
            throw new IllegalArgumentException("contentType 不能为空");
        }
        if (byteSize <= 0L) {
            throw new IllegalArgumentException("byteSize 必须大于 0");
        }

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(resolveBucket(FileStorageScope.PRIVATE))
                .key(targetKey)
                .contentType(contentType)
                .contentLength(byteSize)
                .ifNoneMatch("*")
                .build();
        try {
            PutObjectResponse response = s3Client.putObject(putObjectRequest, RequestBody.fromFile(localFile));
            return response.eTag();
        } catch (S3Exception ex) {
            throw new IllegalStateException("写入文件对象失败 (HTTP " + ex.statusCode() + "): objectKey=" + targetKey, ex);
        } catch (SdkException ex) {
            throw new IllegalStateException("写入文件对象失败: objectKey=" + targetKey, ex);
        }
    }

    @Override
    public String putObject(FileStorageScope scope, String objectKey, byte[] content, String contentType) {
        Objects.requireNonNull(scope, "scope 不能为空");
        if (!StringUtils.hasText(objectKey)) {
            throw new IllegalArgumentException("objectKey 不能为空");
        }
        Objects.requireNonNull(content, "content 不能为空");
        if (content.length == 0) {
            throw new IllegalArgumentException("content 不能为空数组");
        }
        if (!StringUtils.hasText(contentType)) {
            throw new IllegalArgumentException("contentType 不能为空");
        }

        String bucket = resolveBucket(scope);
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .contentLength((long) content.length)
                .build();

        try {
            PutObjectResponse response = s3Client.putObject(putObjectRequest, RequestBody.fromBytes(content));
            return response.eTag();
        } catch (S3Exception ex) {
            throw new IllegalStateException(
                    "写入文件对象失败 (HTTP " + ex.statusCode() + "): bucket=" + bucket + ", objectKey=" + objectKey, ex);
        } catch (SdkException ex) {
            throw new IllegalStateException("写入文件对象失败: bucket=" + bucket + ", objectKey=" + objectKey, ex);
        }
    }

    @Override
    public PresignedDownload presignGet(FileStorageScope scope, String objectKey, Duration ttl) {
        Objects.requireNonNull(scope, "scope 不能为空");
        if (!StringUtils.hasText(objectKey)) {
            throw new IllegalArgumentException("objectKey 不能为空");
        }
        Objects.requireNonNull(ttl, "ttl 不能为空");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl 必须大于 0");
        }

        String bucket = resolveBucket(scope);
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .getObjectRequest(getObjectRequest)
                .signatureDuration(ttl)
                .build();

        PresignedGetObjectRequest presigned;
        try {
            presigned = s3Presigner.presignGetObject(presignRequest);
        } catch (SdkException ex) {
            throw new IllegalStateException("生成文件预签名 GET 链接失败: bucket=" + bucket + ", objectKey=" + objectKey, ex);
        }

        try {
            URI uri = presigned.url().toURI();
            return new PresignedDownload(uri, presigned.expiration());
        } catch (URISyntaxException ex) {
            throw new IllegalStateException("预签名 GET URL 转换 URI 失败", ex);
        }
    }

    @Override
    public void deleteObjects(Collection<StorageObjectRef> objects) {
        if (objects == null || objects.isEmpty()) {
            return;
        }

        Map<FileStorageScope, List<String>> keysByScope = groupKeysByScope(objects);
        List<String> failures = new ArrayList<>();

        for (Map.Entry<FileStorageScope, List<String>> entry : keysByScope.entrySet()) {
            String bucket = resolveBucket(entry.getKey());
            List<String> keys = entry.getValue();
            for (int start = 0; start < keys.size(); start += DELETE_BATCH_SIZE) {
                int end = Math.min(start + DELETE_BATCH_SIZE, keys.size());
                List<ObjectIdentifier> identifiers = keys.subList(start, end)
                        .stream()
                        .map(key -> ObjectIdentifier.builder().key(key).build())
                        .toList();
                DeleteObjectsRequest request = DeleteObjectsRequest.builder()
                        .bucket(bucket)
                        .delete(Delete.builder().objects(identifiers).quiet(true).build())
                        .build();

                DeleteObjectsResponse response;
                try {
                    response = s3Client.deleteObjects(request);
                } catch (SdkException ex) {
                    throw new IllegalStateException(
                            "批量删除对象失败: bucket=" + bucket + ", keyCount=" + identifiers.size(), ex);
                }

                if (response.hasErrors()) {
                    for (S3Error error : response.errors()) {
                        if (ERROR_CODE_NO_SUCH_KEY.equals(error.code())) {
                            continue;
                        }
                        failures.add(bucket + "/" + error.key() + "(" + error.code() + ": " + error.message() + ")");
                    }
                }
            }
        }

        if (!failures.isEmpty()) {
            String summary = failures.stream().limit(5).collect(Collectors.joining("; "));
            throw new IllegalStateException("批量删除存在失败项 (count=" + failures.size() + "): " + summary);
        }
    }

    @Override
    public void deleteQuietly(Collection<StorageObjectRef> objects) {
        if (objects == null || objects.isEmpty()) {
            return;
        }
        Map<FileStorageScope, List<String>> keysByScope = groupKeysByScope(objects);
        for (Map.Entry<FileStorageScope, List<String>> entry : keysByScope.entrySet()) {
            deleteQuietlyInScope(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void deleteQuietlyInScope(FileStorageScope scope, Collection<String> objectKeys) {
        Objects.requireNonNull(scope, "scope 不能为空");
        if (objectKeys == null || objectKeys.isEmpty()) {
            return;
        }

        List<String> validKeys = objectKeys.stream()
                .filter(StringUtils::hasText)
                .map(String::strip)
                .distinct()
                .toList();
        if (validKeys.isEmpty()) {
            return;
        }

        String bucket;
        try {
            bucket = resolveBucket(scope);
        } catch (IllegalStateException ex) {
            log.warn("静默清理跳过未配置的存储桶范围: scope={}, keyCount={}", scope, validKeys.size(), ex);
            return;
        }

        for (int start = 0; start < validKeys.size(); start += DELETE_BATCH_SIZE) {
            int end = Math.min(start + DELETE_BATCH_SIZE, validKeys.size());
            List<ObjectIdentifier> identifiers = validKeys.subList(start, end)
                    .stream()
                    .map(key -> ObjectIdentifier.builder().key(key).build())
                    .toList();

            DeleteObjectsRequest deleteObjectsRequest = DeleteObjectsRequest.builder()
                    .bucket(bucket)
                    .delete(Delete.builder().objects(identifiers).quiet(true).build())
                    .build();
            try {
                DeleteObjectsResponse response = s3Client.deleteObjects(deleteObjectsRequest);
                if (response.hasErrors()) {
                    for (S3Error error : response.errors()) {
                        if (ERROR_CODE_NO_SUCH_KEY.equals(error.code())) {
                            continue;
                        }
                        log.warn("批量清理对象存储残留文件存在单项失败, bucket={}, objectKey={}, code={}, message={}",
                                bucket, error.key(), error.code(), error.message());
                    }
                }
            } catch (SdkException ex) {
                log.warn("批量清理对象存储残留文件失败, bucket={}, keyCount={}", bucket, identifiers.size(), ex);
            }
        }
    }

    private String resolveBucket(FileStorageScope scope) {
        return switch (scope) {
            case PRIVATE -> s3Properties.getBucket();
            case DELIVERY -> {
                String deliveryBucket = s3Properties.getDeliveryBucket();
                if (!StringUtils.hasText(deliveryBucket)) {
                    throw new IllegalStateException("公开交付存储桶（tsuki.s3.delivery-bucket）未配置");
                }
                yield deliveryBucket.strip();
            }
        };
    }

    private Map<FileStorageScope, List<String>> groupKeysByScope(Collection<StorageObjectRef> objects) {
        Map<FileStorageScope, Set<String>> deduped = new EnumMap<>(FileStorageScope.class);
        for (StorageObjectRef ref : objects) {
            if (ref == null) {
                continue;
            }
            deduped.computeIfAbsent(ref.scope(), key -> new LinkedHashSet<>()).add(ref.objectKey());
        }
        Map<FileStorageScope, List<String>> grouped = new EnumMap<>(FileStorageScope.class);
        deduped.forEach((scope, keys) -> {
            if (!keys.isEmpty()) {
                grouped.put(scope, new ArrayList<>(keys));
            }
        });
        return grouped;
    }
}

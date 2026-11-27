package com.nep.tsuki.file.storage.s3;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.springframework.util.StringUtils;

import com.nep.tsuki.file.processing.FileJobCode;
import com.nep.tsuki.file.storage.FileObjectStorage;
import com.nep.tsuki.infra.storage.s3.S3Properties;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
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

@Slf4j
public class S3FileObjectStorage implements FileObjectStorage {
    private static final int COPY_BUFFER_SIZE = 8192;
    private static final int DELETE_BATCH_SIZE = 1000;

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    public S3FileObjectStorage(S3Client s3Client, S3Properties s3Properties) {
        this.s3Client = Objects.requireNonNull(s3Client, "s3Client 不能为空");
        this.s3Properties = Objects.requireNonNull(s3Properties, "s3Properties 不能为空");
    }

    @Override
    public FileJobCode downloadToFile(String objectKey, String expectedEtag, long expectedByteSize,
            Path targetFile) {
        if (!StringUtils.hasText(objectKey)) {
            throw new IllegalArgumentException("objectKey 不能为空");
        }
        if (expectedByteSize <= 0L) {
            throw new IllegalArgumentException("expectedByteSize 必须大于 0");
        }
        Objects.requireNonNull(targetFile, "targetFile 不能为空");

        GetObjectRequest.Builder requestBuilder = GetObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(objectKey);
        if (StringUtils.hasText(expectedEtag)) {
            requestBuilder.ifMatch(expectedEtag);
        }

        try (
                ResponseInputStream<GetObjectResponse> inputStream = s3Client.getObject(requestBuilder.build());
                OutputStream outputStream = Files.newOutputStream(targetFile)) {

            Long responseLength = inputStream.response().contentLength();
            if (responseLength != null && responseLength.longValue() != expectedByteSize) {
                log.warn("源对象响应长度与记录不符: objectKey={}, expected={}, actual={}",
                        objectKey, expectedByteSize, responseLength);
                return FileJobCode.SOURCE_CHANGED;
            }

            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            long totalBytesRead = 0L;
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                totalBytesRead += bytesRead;
                if (totalBytesRead > expectedByteSize) {
                    log.warn("源对象实际读取字节超过预期: objectKey={}, expected={}", objectKey, expectedByteSize);
                    return FileJobCode.SOURCE_CHANGED;
                }
                outputStream.write(buffer, 0, bytesRead);
            }

            if (totalBytesRead != expectedByteSize) {
                log.warn("源对象实际读取字节不足预期: objectKey={}, expected={}, actual={}",
                        objectKey, expectedByteSize, totalBytesRead);
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
                        "读取文件对象失败 (HTTP " + ex.statusCode() + "): objectKey=" + objectKey, ex);
            };
        } catch (SdkException | IOException ex) {
            throw new IllegalStateException("读取文件对象失败: objectKey=" + objectKey, ex);
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

        return putObjectIfAbsent(targetKey, contentType, byteSize, RequestBody.fromFile(localFile));
    }

    @Override
    public void deleteQuietly(Collection<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return;
        }

        List<String> validKeys = objectKeys
                .stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (validKeys.isEmpty()) {
            return;
        }

        for (int start = 0; start < validKeys.size(); start += DELETE_BATCH_SIZE) {
            int end = Math.min(start + DELETE_BATCH_SIZE, validKeys.size());
            List<ObjectIdentifier> identifiers = validKeys.subList(start, end)
                    .stream()
                    .map(key -> ObjectIdentifier.builder().key(key).build())
                    .toList();

            Delete delete = Delete.builder()
                    .objects(identifiers)
                    .quiet(true)
                    .build();

            DeleteObjectsRequest deleteObjectsRequest = DeleteObjectsRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .delete(delete)
                    .build();
            try {
                DeleteObjectsResponse response = s3Client.deleteObjects(deleteObjectsRequest);
                if (response.hasErrors()) {
                    for (S3Error error : response.errors()) {
                        log.warn("批量清理对象存储残留文件存在单项失败, bucket={}, objectKey={}, code={}, message={}",
                                s3Properties.getBucket(), error.key(), error.code(), error.message());
                    }
                }
            } catch (SdkException ex) {
                log.warn("批量清理对象存储残留文件失败, bucket={}, keyCount={}", s3Properties.getBucket(), identifiers.size(), ex);
            }
        }
    }

    @Override
    public String putObjectIfAbsent(String objectKey, byte[] content, String contentType) {
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

        return putObjectIfAbsent(objectKey, contentType, (long) content.length, RequestBody.fromBytes(content));
    }

    private String putObjectIfAbsent(
            String objectKey,
            String contentType,
            long byteSize,
            RequestBody requestBody) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(byteSize)
                .ifNoneMatch("*")
                .build();

        try {
            PutObjectResponse response = s3Client.putObject(putObjectRequest, requestBody);
            return response.eTag();
        } catch (S3Exception ex) {
            throw new IllegalStateException("写入文件对象失败 (HTTP " + ex.statusCode() + "): objectKey=" + objectKey, ex);
        } catch (SdkException ex) {
            throw new IllegalStateException("写入文件对象失败: objectKey=" + objectKey, ex);
        }
    }
}

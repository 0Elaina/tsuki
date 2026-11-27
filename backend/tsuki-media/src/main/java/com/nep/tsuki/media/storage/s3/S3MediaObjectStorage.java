package com.nep.tsuki.media.storage.s3;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.springframework.util.StringUtils;

import com.nep.tsuki.infra.storage.s3.S3Properties;
import com.nep.tsuki.media.processing.MediaJobCode;
import com.nep.tsuki.media.storage.MediaObjectStorage;

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
public class S3MediaObjectStorage implements MediaObjectStorage {
    private static final int COPY_BUFFER_SIZE = 8192;
    private static final int DELETE_BATCH_SIZE = 1000;

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    public S3MediaObjectStorage(S3Client s3Client, S3Properties s3Properties) {
        this.s3Client = Objects.requireNonNull(s3Client, "s3Client 不能为空");
        this.s3Properties = Objects.requireNonNull(s3Properties, "s3Properties 不能为空");
    }

    /**
     * 从对象存储下载媒体文件到本地指定路径，并在下载过程中校验对象的一致性
     *
     * @param objectKey        对象存储中的文件 Key
     * @param expectedEtag     期望的对象 ETag
     * @param expectedByteSize 期望的文件字节大小
     * @param targetFile       本地目标文件路径
     * @return 下载结果状态
     */
    @Override
    public MediaJobCode downloadToFile(String objectKey, String expectedEtag, long expectedByteSize,
            Path targetFile) {
        // 基础参数校验
        if (!StringUtils.hasText(objectKey)) {
            throw new IllegalArgumentException("objectKey 不能为空");
        }
        if (expectedByteSize <= 0L) {
            throw new IllegalArgumentException("expectedByteSize 必须大于0");
        }
        Objects.requireNonNull(targetFile, "targetFile 不能为空");

        // 构建 S3 GetObject 请求，若指定了 ETag 则附加 If-Match 条件头
        GetObjectRequest.Builder requestBuilder = GetObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(objectKey);
        if (StringUtils.hasText(expectedEtag)) {
            requestBuilder.ifMatch(expectedEtag);
        }

        // 建立流连接并写入本地文件
        try (
                ResponseInputStream<GetObjectResponse> inputStream = s3Client.getObject(requestBuilder.build());
                OutputStream outputStream = Files.newOutputStream(targetFile)) {

            // 前置检查：比对响应头中的 Content-Length，若明显不符则提前终止，避免无效流量下载
            Long responseLength = inputStream.response().contentLength();
            if (responseLength != null && responseLength.longValue() != expectedByteSize) {
                log.warn("源对象响应长度与记录不符: objectKey={}, expected={}, actual={}",
                        objectKey, expectedByteSize, responseLength);
                return MediaJobCode.SOURCE_CHANGED;
            }

            // 流式读取与实时熔断：防止恶意构造或分块传输导致的实际字节数超出预期（防磁盘打满）
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            long totalBytesRead = 0L;
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                totalBytesRead += bytesRead;
                if (totalBytesRead > expectedByteSize) {
                    log.warn("源对象实际读取字节超过预期: objectKey={}, expected={}", objectKey, expectedByteSize);
                    return MediaJobCode.SOURCE_CHANGED;
                }
                outputStream.write(buffer, 0, bytesRead);
            }

            // 完整性校验：流读取结束后确认总字节数与预期完全一致（防止网络截断导致文件不完整）
            if (totalBytesRead != expectedByteSize) {
                log.warn("源对象实际读取字节不足预期: objectKey={}, expected={}, actual={}",
                        objectKey, expectedByteSize, totalBytesRead);
                return MediaJobCode.SOURCE_CHANGED;
            }

            return MediaJobCode.SUCCESS;
        } catch (NoSuchKeyException ex) {
            // 明确捕获 Key 不存在异常
            return MediaJobCode.SOURCE_MISSING;
        } catch (S3Exception ex) {
            // 兼容部分 S3 兼容存储直接返回 HTTP 状态码的情况
            return switch (ex.statusCode()) {
                case 404 -> MediaJobCode.SOURCE_MISSING;
                case 412 -> MediaJobCode.SOURCE_CHANGED;
                default -> throw new IllegalStateException(
                        "读取媒体对象失败 (HTTP " + ex.statusCode() + "): objectKey=" + objectKey, ex);
            };
        } catch (SdkException | IOException ex) {
            // 兜底处理网络超时、SDK 内部错误或本地文件写入异常
            throw new IllegalStateException("读取媒体对象失败: objectKey=" + objectKey, ex);
        }
    }

    /**
     * 将本地已校验的源文件归档上传到目标路径
     *
     * @param sourceKey   源对象存储 Key
     * @param sourceEtag  源对象 ETag
     * @param targetKey   归档目标对象存储 Key
     * @param localFile   已下载并校验通过的本地临时文件路径
     * @param contentType 媒体文件 MIME 类型
     * @param byteSize    本地文件精确字节大小
     * @return 上传成功后由服务端生成的目标对象 ETag
     */
    @Override
    public String archiveObject(String sourceKey, String sourceEtag, String targetKey, Path localFile,
            String contentType, long byteSize) {
        // 前置参数合法性校验
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

        // 基于本地文件流执行原子防覆盖写入
        return putObjectIfAbsent(targetKey, contentType, byteSize, RequestBody.fromFile(localFile));
    }

    /**
     * 静默批量删除对象存储中的指定 Key 集合
     * 
     * @param objectKeys 待清理的对象存储 Key 集合
     */
    @Override
    public void deleteQuietly(Collection<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return;
        }

        // 过滤空字符串/纯空白 Key 并去重，避免无效条目或重复删除请求
        List<String> validKeys = objectKeys
                .stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (validKeys.isEmpty()) {
            return;
        }

        // 按固定批次大小切分遍历（S3 DeleteObjects 单次请求上限为 1000 个对象）
        for (int start = 0; start < validKeys.size(); start += DELETE_BATCH_SIZE) {
            int end = Math.min(start + DELETE_BATCH_SIZE, validKeys.size());
            List<ObjectIdentifier> identifiers = validKeys.subList(start, end)
                    .stream()
                    .map(key -> ObjectIdentifier.builder().key(key).build())
                    .toList();

            // 启用 quiet 模式，使服务端仅在响应体中返回删除失败的条目，减小网络传输开销
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
                // S3 批量删除为部分成功语义
                if (response.hasErrors()) {
                    for (S3Error error : response.errors()) {
                        log.warn("批量清理对象存储残留文件存在单项失败, bucket={}, objectKey={}, code={}, message={}",
                                s3Properties.getBucket(), error.key(), error.code(), error.message());
                    }
                }
            } catch (SdkException ex) {
                // 捕获整批请求的协议或网络异常，静默记录日志并继续清理下一批次
                log.warn("批量清理对象存储残留文件失败, bucket={}, keyCount={}", s3Properties.getBucket(), identifiers.size(), ex);
            }
        }
    }

    /**
     * 将内存中的字节数组原子写入对象存储
     *
     * @param objectKey   目标对象存储 Key
     * @param content     待上传的非空字节数组
     * @param contentType 媒体文件 MIME 类型
     * @return 上传成功后由服务端生成的对象 ETag
     */
    @Override
    public String putObjectIfAbsent(String objectKey, byte[] content, String contentType) {
        // 前置参数与非空内容校验
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

        // 封装内存字节数组并委托底层原子写入方法
        return putObjectIfAbsent(objectKey, contentType, (long) content.length, RequestBody.fromBytes(content));
    }

    /**
     * 原子写入媒体对象到对象存储
     *
     * @param objectKey   目标对象存储路径 Key
     * @param contentType 媒体文件的 MIME 类型
     * @param byteSize    上传数据的精确字节大小
     * @param requestBody AWS SDK 封装的请求体数据源
     * @return 上传成功后由服务端生成并返回的对象 ETag
     */
    private String putObjectIfAbsent(
            String objectKey,
            String contentType,
            long byteSize,
            RequestBody requestBody) {
        // 构建上传请求，通过 ifNoneMatch("*") 实现服务端原子级“不存在才写入”语义
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(byteSize)
                .ifNoneMatch("*")
                .build();

        try {
            // 执行同步上传并提取服务端生成的 ETag，用于后续一致性校验
            PutObjectResponse response = s3Client.putObject(putObjectRequest, requestBody);
            return response.eTag();
        } catch (S3Exception ex) {
            throw new IllegalStateException("写入媒体对象失败 (HTTP " + ex.statusCode() + "): objectKey=" + objectKey, ex);
        } catch (SdkException ex) {
            throw new IllegalStateException("写入媒体对象失败: objectKey=" + objectKey, ex);
        }
    }
}

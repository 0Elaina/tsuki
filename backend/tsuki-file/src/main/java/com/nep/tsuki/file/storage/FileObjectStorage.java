package com.nep.tsuki.file.storage;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;

import com.nep.tsuki.file.model.enums.FileStorageScope;
import com.nep.tsuki.file.processing.FileJobCode;
import com.nep.tsuki.file.processing.FileProcessResult;

public interface FileObjectStorage {

    /**
     * 从指定逻辑桶下载文件到本地路径，并在下载过程中核对 ETag 与精确字节数
     */
    FileJobCode downloadToFile(
            FileStorageScope scope,
            String objectKey,
            String expectedEtag,
            long expectedByteSize,
            Path targetFile);

    /**
     * 使用 S3 CopyObject 归档对象（仅带源端 CopySourceIfMatch 条件，成功返回目标 ETag）
     */
    FileProcessResult<String> copyObjectIfMatch(
            FileStorageScope sourceScope,
            String sourceKey,
            String sourceEtag,
            FileStorageScope targetScope,
            String targetKey);

    /**
     * 使用普通 PUT 向指定逻辑桶写入尝试级独占对象（如衍生物产物）
     */
    String putObject(FileStorageScope scope, String objectKey, byte[] content, String contentType);

    /**
     * 为指定逻辑桶的对象签发临时 GET 预签名访问地址
     */
    URI presignGet(FileStorageScope scope, String objectKey, Duration ttl);

    /**
     * 在指定逻辑桶内执行严格批量删除：对象已不存在（NoSuchKey）视为成功，任何其他单项或批次失败抛出异常
     */
    void deleteObjects(FileStorageScope scope, Collection<String> objectKeys);

    /**
     * 在指定逻辑桶内静默批量清理对象（用于尝试产物补偿与租期回收，记录日志但不抛异常）
     */
    void deleteQuietly(FileStorageScope scope, Collection<String> objectKeys);
}

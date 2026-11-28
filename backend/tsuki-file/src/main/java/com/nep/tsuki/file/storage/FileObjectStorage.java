package com.nep.tsuki.file.storage;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;

import com.nep.tsuki.file.model.dto.ObjectCopyOutcome;
import com.nep.tsuki.file.model.dto.PresignedDownload;
import com.nep.tsuki.file.model.dto.StorageObjectRef;
import com.nep.tsuki.file.model.enums.FileStorageScope;
import com.nep.tsuki.file.processing.FileJobCode;

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
     * 从私有桶下载文件到本地路径（便捷重载）
     */
    default FileJobCode downloadToFile(String objectKey, String expectedEtag, long expectedByteSize, Path targetFile) {
        return downloadToFile(FileStorageScope.PRIVATE, objectKey, expectedEtag, expectedByteSize, targetFile);
    }

    /**
     * 使用 S3 CopyObject 归档对象（仅带源端 CopySourceIfMatch 条件，源对象变化或不存在时返回对应错误码）
     */
    ObjectCopyOutcome copyObjectIfMatch(
            FileStorageScope sourceScope,
            String sourceKey,
            String sourceEtag,
            FileStorageScope targetScope,
            String targetKey);

    /**
     * 兼容旧归档方法（将在执行器升级为 CopyObject 后移除）
     */
    String archiveObject(
            String sourceKey,
            String sourceEtag,
            String targetKey,
            Path localFile,
            String contentType,
            long byteSize);

    /**
     * 使用普通 PUT 向指定逻辑桶写入尝试级独占对象（如衍生物产物）
     */
    String putObject(FileStorageScope scope, String objectKey, byte[] content, String contentType);

    /**
     * 为指定逻辑桶的对象签发临时 GET 预签名下载链接
     */
    PresignedDownload presignGet(FileStorageScope scope, String objectKey, Duration ttl);

    /**
     * 按逻辑桶分组执行严格批量删除：对象已不存在（NoSuchKey）视为成功，任何其他单项或批次失败抛出异常
     */
    void deleteObjects(Collection<StorageObjectRef> objects);

    /**
     * 按逻辑桶分组静默批量清理对象（用于尝试产物补偿与租期回收，记录日志但不抛异常）
     */
    void deleteQuietly(Collection<StorageObjectRef> objects);

    /**
     * 静默批量清理私有桶中的指定 Key 集合
     */
    void deleteQuietlyInScope(FileStorageScope scope, Collection<String> objectKeys);

    /**
     * 兼容旧私有桶静默清理入口
     */
    default void deleteQuietly(Collection<String> objectKeys) {
        deleteQuietlyInScope(FileStorageScope.PRIVATE, objectKeys);
    }
}

package com.nep.tsuki.file.storage;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;

import com.nep.tsuki.file.model.dto.ObjectCopyResult;
import com.nep.tsuki.file.model.enums.FileStorageScope;
import com.nep.tsuki.file.processing.FileJobCode;

/**
 * 后台文件对象存储操作契约，按逻辑存储范围（PRIVATE / DELIVERY）路由至对应物理桶
 */
public interface FileObjectStorage {

    /**
     * 从指定逻辑桶下载对象到本地临时文件，并在读取过程中核对 ETag 与精确字节数
     *
     * @param scope            源对象逻辑桶范围
     * @param objectKey        源对象完整键
     * @param expectedEtag     期望的源对象 ETag（用于条件读取 If-Match）
     * @param expectedByteSize 期望的精确字节大小
     * @param targetFile       本地目标文件路径
     * @return 下载与一致性校验状态码
     */
    FileJobCode downloadToFile(
            FileStorageScope scope,
            String objectKey,
            String expectedEtag,
            long expectedByteSize,
            Path targetFile);

    /**
     * 使用 S3 CopyObject 归档对象（仅带源端 CopySourceIfMatch 条件，源变化或不存在时返回对应状态码）
     *
     * @param sourceScope 源对象逻辑桶范围
     * @param sourceKey   源对象完整键
     * @param sourceEtag  源对象 ETag（作为 CopySourceIfMatch 条件）
     * @param targetScope 目标对象逻辑桶范围
     * @param targetKey   目标对象完整键
     * @return 复制结果，成功时包含目标对象 ETag
     */
    ObjectCopyResult copyObjectIfMatch(
            FileStorageScope sourceScope,
            String sourceKey,
            String sourceEtag,
            FileStorageScope targetScope,
            String targetKey);

    /**
     * 使用普通 PUT 向指定逻辑桶写入尝试级独占对象（如衍生物产物）
     *
     * @param scope       目标逻辑桶范围
     * @param objectKey   尝试级独占对象键
     * @param content     非空对象字节内容
     * @param contentType 对象 MIME 类型
     * @return 服务端返回的目标对象 ETag
     */
    String putObject(FileStorageScope scope, String objectKey, byte[] content, String contentType);

    /**
     * 为指定逻辑桶的对象签发临时 GET 预签名访问地址
     *
     * @param scope     对象逻辑桶范围
     * @param objectKey 对象完整键
     * @param ttl       预签名有效期
     * @return 可供客户端临时访问的预签名 GET URI
     */
    URI presignGet(FileStorageScope scope, String objectKey, Duration ttl);

    /**
     * 在指定逻辑桶内执行严格批量删除：对象已不存在（NoSuchKey）视为成功，任何其他单项或批次失败抛出异常
     *
     * @param scope      目标逻辑桶范围
     * @param objectKeys 待删除的对象键集合
     */
    void deleteObjects(FileStorageScope scope, Collection<String> objectKeys);

    /**
     * 在指定逻辑桶内静默批量清理对象（用于尝试产物补偿与租期回收，记录告警日志但不抛出异常）
     *
     * @param scope      目标逻辑桶范围
     * @param objectKeys 待清理的对象键集合
     */
    void deleteQuietly(FileStorageScope scope, Collection<String> objectKeys);
}

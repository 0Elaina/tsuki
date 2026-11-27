package com.nep.tsuki.file.storage;

import java.util.Optional;

import com.nep.tsuki.file.model.dto.PresignedUpload;
import com.nep.tsuki.file.model.dto.StagedObjectMetadata;
import com.nep.tsuki.file.model.dto.StagingObjectKey;

/**
 * 暂存区上传存储接口（用于管理客户端直传的临时对象）
 */
public interface StagedUploadStorage {

    /**
     * 生成文件直传的预签名凭证（HTTP PUT）
     *
     * @param key         暂存对象键
     * @param contentType 文件媒体类型（MIME Type）
     * @param byteSize    预期上传的文件字节大小
     * @param maxBytes    该文件类型允许的最大字节数上限
     * @return 预签名上传凭证（包含上传地址、过期时间及必需请求头）
     */
    PresignedUpload presignPut(StagingObjectKey key, String contentType, long byteSize, long maxBytes);

    /**
     * 查询暂存对象的元数据
     *
     * @param objectKey 数据库保存的完整暂存对象键
     * @return 若对象存在则返回其元数据，否则返回 {@link Optional#empty()}
     */
    Optional<StagedObjectMetadata> find(String objectKey);
}

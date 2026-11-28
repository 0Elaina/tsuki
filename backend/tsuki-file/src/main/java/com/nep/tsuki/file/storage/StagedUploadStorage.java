package com.nep.tsuki.file.storage;

import java.util.Optional;

import com.nep.tsuki.file.model.dto.PresignedUpload;
import com.nep.tsuki.file.model.dto.StagedObjectMetadata;

/**
 * 暂存区上传存储接口（用于管理客户端直传的临时对象）
 */
public interface StagedUploadStorage {

    /**
     * 生成文件直传的预签名凭证（HTTP PUT）
     */
    PresignedUpload presignPut(String stagingObjectKey, String contentType, long byteSize, long maxBytes);

    /**
     * 查询暂存对象的元数据
     */
    Optional<StagedObjectMetadata> find(String objectKey);
}

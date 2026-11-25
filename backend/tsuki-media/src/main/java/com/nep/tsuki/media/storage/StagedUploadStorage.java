package com.nep.tsuki.media.storage;

import java.util.Optional;

/**
 * 暂存区上传存储接口（用于管理客户端直传的临时对象）
 */
public interface StagedUploadStorage {

    /**
     * 生成图片直传的预签名凭证（HTTP PUT）
     *
     * @param key         暂存对象键
     * @param contentType 图片媒体类型（MIME Type）
     * @param byteSize    预期上传的文件字节大小
     * @return 预签名上传凭证（包含上传地址、过期时间及必需请求头）
     */
    PresignedImageUpload presignImagePut(StagingObjectKey key, String contentType, long byteSize);

    /**
     * 查询暂存对象的元数据
     *
     * @param objectKey 数据库保存的完整暂存对象键
     * @return 若对象存在则返回其元数据，否则返回 {@link Optional#empty()}
     */
    Optional<StagedObjectMetadata> find(String objectKey);
}
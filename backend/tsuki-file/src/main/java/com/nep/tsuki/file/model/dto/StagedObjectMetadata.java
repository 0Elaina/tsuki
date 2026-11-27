package com.nep.tsuki.file.model.dto;

/**
 * 暂存对象的元数据
 *
 * @param byteSize    对象大小
 * @param contentType 媒体类型
 * @param eTag        对象校验标签
 */
public record StagedObjectMetadata(
        long byteSize,
        String contentType,
        String eTag) {

    public StagedObjectMetadata {
        if (byteSize < 0L) {
            throw new IllegalArgumentException("byteSize 不能为负数: " + byteSize);
        }
    }
}

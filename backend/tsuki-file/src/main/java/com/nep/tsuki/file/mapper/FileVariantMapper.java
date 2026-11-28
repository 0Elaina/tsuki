package com.nep.tsuki.file.mapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nep.tsuki.file.model.entity.FileVariant;

@Mapper
public interface FileVariantMapper extends BaseMapper<FileVariant> {

    /**
     * 按主键加行级排他锁查询文件衍生物
     */
    FileVariant selectByIdForUpdate(@Param("id") long id);

    /**
     * 按资产主键顺序加行级排他锁查询关联的全部衍生物
     */
    List<FileVariant> selectByAssetIdForUpdate(@Param("assetId") long assetId);

    /**
     * 原子推进衍生物为 READY 并写入产物定位、大小、ETag 与扩展元数据
     */
    int markReady(
            @Param("variantId") long variantId,
            @Param("expectedStatus") String expectedStatus,
            @Param("targetStatus") String targetStatus,
            @Param("objectKey") String objectKey,
            @Param("contentType") String contentType,
            @Param("byteSize") long byteSize,
            @Param("etag") String etag,
            @Param("metadata") Map<String, Object> metadata,
            @Param("now") OffsetDateTime now);

    /**
     * 原子推进衍生物为 FAILED 并记录最近一次失败原因
     */
    int markFailed(
            @Param("variantId") long variantId,
            @Param("expectedStatus") String expectedStatus,
            @Param("targetStatus") String targetStatus,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("now") OffsetDateTime now);
}

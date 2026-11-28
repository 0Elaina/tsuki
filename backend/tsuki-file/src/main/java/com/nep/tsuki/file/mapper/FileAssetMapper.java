package com.nep.tsuki.file.mapper;

import java.time.OffsetDateTime;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nep.tsuki.file.model.entity.FileAsset;

@Mapper
public interface FileAssetMapper extends BaseMapper<FileAsset> {

    /**
     * 按主键加行级排他锁查询文件资产（用于严格遵循 Asset -> Variant -> Job 锁序）
     */
    FileAsset selectByIdForUpdate(@Param("id") long id);

    /**
     * 将归档完成的文件资产推进为 READY 并写入正式原件键、ETag、探测元数据与未绑定过期截止时间
     */
    int markReady(
            @Param("assetId") long assetId,
            @Param("expectedStatus") String expectedStatus,
            @Param("targetStatus") String targetStatus,
            @Param("objectKey") String objectKey,
            @Param("etag") String etag,
            @Param("metadata") Map<String, Object> metadata,
            @Param("unboundExpiresAt") OffsetDateTime unboundExpiresAt,
            @Param("now") OffsetDateTime now);

    /**
     * 将校验或处理失败的文件资产推进为 REJECTED 或 FAILED 并记录原因
     */
    int markValidationTerminalFailure(
            @Param("assetId") long assetId,
            @Param("expectedStatus") String expectedStatus,
            @Param("targetStatus") String targetStatus,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("now") OffsetDateTime now);
}

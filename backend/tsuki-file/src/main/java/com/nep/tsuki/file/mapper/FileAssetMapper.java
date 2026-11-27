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
     * 批量删除过期的未确认记录
     *
     * @param cutoff    过期截止时间
     * @param batchSize 单次批量删除的最大数量限制
     * @return 实际删除的记录条数
     */
    int deleteExpiredUnconfirmedBatch(@Param("cutoff") OffsetDateTime cutoff, @Param("batchSize") int batchSize);

    /**
     * 将归档完成的文件资产推进为就绪态并写入原件路径、ETag 与探测元数据
     *
     * @param assetId        文件资产主键 ID
     * @param expectedStatus 乐观校验的当前资产状态
     * @param targetStatus   目标资产状态
     * @param objectKey      归档后的正式对象键
     * @param etag           归档后的对象 ETag
     * @param metadata       探测到的原件扩展元数据（可为 null）
     * @param now            当前 UTC 时间
     * @return 实际更新的记录条数
     */
    int markReady(
            @Param("assetId") long assetId,
            @Param("expectedStatus") String expectedStatus,
            @Param("targetStatus") String targetStatus,
            @Param("objectKey") String objectKey,
            @Param("etag") String etag,
            @Param("metadata") Map<String, Object> metadata,
            @Param("now") OffsetDateTime now);
}

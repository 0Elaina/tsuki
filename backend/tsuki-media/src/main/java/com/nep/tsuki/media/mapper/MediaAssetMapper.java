package com.nep.tsuki.media.mapper;

import java.time.OffsetDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nep.tsuki.media.model.entity.MediaAsset;

@Mapper
public interface MediaAssetMapper extends BaseMapper<MediaAsset> {

    /**
     * 批量删除过期的未确认记录
     *
     * @param cutoff    过期截止时间
     * @param batchSize 单次批量删除的最大数量限制
     * @return 实际删除的记录条数
     */
    int deleteExpiredUnconfirmedBatch(@Param("cutoff") OffsetDateTime cutoff, @Param("batchSize") int batchSize);
}

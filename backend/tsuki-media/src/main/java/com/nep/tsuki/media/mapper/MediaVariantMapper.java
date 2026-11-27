package com.nep.tsuki.media.mapper;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nep.tsuki.media.model.entity.MediaVariant;

@Mapper
public interface MediaVariantMapper extends BaseMapper<MediaVariant> {

    /**
     * 批量插入媒体衍生规格记录
     *
     * @param variants 待插入的媒体规格列表
     * @param now      统一写入的创建与更新时间
     * @return 实际插入的记录数
     */
    int insertBatch(@Param("variants") List<MediaVariant> variants, @Param("now") OffsetDateTime now);
}

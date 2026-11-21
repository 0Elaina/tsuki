package com.nep.tsuki.media.mapper;

import java.time.OffsetDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nep.tsuki.media.model.entity.MediaAsset;

@Mapper
public interface MediaAssetMapper extends BaseMapper<MediaAsset> {

    /**
     * 获取指定 ID 的媒体资产，并加锁
     *
     * @param id 媒体资产 ID
     * @return 媒体资产
     */
    @Select("SELECT id, kind, status, cleanup_at, created_at, updated_at FROM media_assets WHERE id = #{id} FOR UPDATE")
    MediaAsset selectByIdForUpdate(@Param("id") Long id);

    /**
     * 查询并锁定一条已到期的待清理媒体资产记录
     *
     * 
     * @param now 当前时间
     * @return 加锁后的媒体资产
     */
    MediaAsset selectOneExpiredForUpdateSkipLocked(@Param("now") OffsetDateTime now);
}

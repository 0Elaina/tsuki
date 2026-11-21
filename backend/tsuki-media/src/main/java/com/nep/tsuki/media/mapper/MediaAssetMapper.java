package com.nep.tsuki.media.mapper;

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
}

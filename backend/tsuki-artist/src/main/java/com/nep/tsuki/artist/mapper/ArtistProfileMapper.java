package com.nep.tsuki.artist.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nep.tsuki.artist.model.entity.ArtistProfile;

@Mapper
public interface ArtistProfileMapper extends BaseMapper<ArtistProfile> {

}

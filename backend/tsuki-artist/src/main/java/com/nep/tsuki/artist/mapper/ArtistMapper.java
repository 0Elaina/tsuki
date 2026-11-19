package com.nep.tsuki.artist.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nep.tsuki.artist.model.entity.Artist;

@Mapper
public interface ArtistMapper extends BaseMapper<Artist> {

}

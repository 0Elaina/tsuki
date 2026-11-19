package com.nep.tsuki.artist.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nep.tsuki.artist.model.entity.ArtistAlias;

@Mapper
public interface ArtistAliasMapper extends BaseMapper<ArtistAlias> {

    /**
     * 插入别名，如果别名已存在则返回0
     *
     * @param alias 别名
     * @return 插入结果
     */
    int insertIfNameUnique(ArtistAlias alias);
}

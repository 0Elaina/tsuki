package com.nep.tsuki.tag.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nep.tsuki.tag.model.entity.Tag;

@Mapper
public interface TagMapper extends BaseMapper<Tag> {

}

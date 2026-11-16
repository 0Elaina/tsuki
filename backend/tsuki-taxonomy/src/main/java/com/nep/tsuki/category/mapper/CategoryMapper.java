package com.nep.tsuki.category.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nep.tsuki.category.model.entity.Category;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

}

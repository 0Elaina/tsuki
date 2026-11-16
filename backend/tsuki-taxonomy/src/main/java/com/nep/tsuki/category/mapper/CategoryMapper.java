package com.nep.tsuki.category.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nep.tsuki.category.model.entity.Category;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    /**
     * 根据 ID 批量查询分区并加锁
     *
     * @param ids id列表
     * @return 分区列表
     */
    List<Category> selectByIdsForUpdate(@Param("ids") List<Long> ids);
}

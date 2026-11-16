package com.nep.tsuki.tag.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nep.tsuki.tag.model.entity.Tag;

@Mapper
public interface TagMapper extends BaseMapper<Tag> {

    /**
     * 根据 ID 批量查询标签并加锁
     *
     * @param ids ID 列表
     * @return 标签列表
     */
    List<Tag> selectByIdsForUpdate(@Param("ids") List<Long> ids);
}

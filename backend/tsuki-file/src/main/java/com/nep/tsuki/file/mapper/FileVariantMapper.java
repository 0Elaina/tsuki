package com.nep.tsuki.file.mapper;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nep.tsuki.file.model.entity.FileVariant;

@Mapper
public interface FileVariantMapper extends BaseMapper<FileVariant> {

    /**
     * 批量插入文件交付规格记录
     *
     * @param variants 待插入的文件规格列表
     * @param now      统一写入的创建与更新时间
     * @return 实际插入的记录数
     */
    int insertBatch(@Param("variants") List<FileVariant> variants, @Param("now") OffsetDateTime now);
}

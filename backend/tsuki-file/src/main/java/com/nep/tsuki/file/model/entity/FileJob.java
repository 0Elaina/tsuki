package com.nep.tsuki.file.model.entity;

import java.time.OffsetDateTime;
import java.util.Map;

import org.apache.ibatis.type.JdbcType;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nep.tsuki.infra.persistence.mybatis.PostgresJsonbTypeHandler;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName(value = "file_jobs", autoResultMap = true)
public class FileJob {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long assetId;
    private Long variantId;
    private String type;
    private String status;

    private Integer attemptCount;
    private OffsetDateTime availableAt;
    private OffsetDateTime leaseUntil;
    private OffsetDateTime dispatchAfter;
    private String dispatchToken;
    private OffsetDateTime dispatchedAt;
    private String dedupKey;

    @TableField(jdbcType = JdbcType.OTHER, typeHandler = PostgresJsonbTypeHandler.class)
    private Map<String, Object> payload;

    private String failureCode;
    private String failureMessage;
    private OffsetDateTime finishedAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}

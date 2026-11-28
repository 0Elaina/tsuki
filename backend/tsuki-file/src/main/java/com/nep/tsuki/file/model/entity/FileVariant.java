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
@TableName(value = "file_variants", autoResultMap = true)
public class FileVariant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long assetId;
    private String code;
    private Integer presetRevision;
    private String status;
    private String storageScope;
    private String objectKey;
    private String contentType;
    private Long byteSize;
    private String etag;

    @TableField(jdbcType = JdbcType.OTHER, typeHandler = PostgresJsonbTypeHandler.class)
    private Map<String, Object> metadata;

    private String failureCode;
    private String failureMessage;
    private OffsetDateTime withdrawnAt;
    private OffsetDateTime purgedAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}

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
@TableName(value = "file_assets", autoResultMap = true)
public class FileAsset {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long uploaderAccountId;
    private String kind;
    private String status;
    private String storageScope;
    private String objectKey;
    private String contentType;
    private Long byteSize;
    private String etag;
    private String originalFilename;

    @TableField(jdbcType = JdbcType.OTHER, typeHandler = PostgresJsonbTypeHandler.class)
    private Map<String, Object> metadata;

    private OffsetDateTime confirmExpiresAt;
    private OffsetDateTime confirmedAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}

package com.nep.tsuki.media.model.entity;

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
@TableName(value = "media_variants", autoResultMap = true)
public class MediaVariant {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的原始媒体资源 ID */
    private Long assetId;

    /** 变体规格编码 */
    private String code;

    /** 对象存储（OSS/S3）中的文件存储路径键 */
    private String objectKey;

    private String contentType;
    private Long byteSize;
    private Integer width;
    private Integer height;

    @TableField(jdbcType = JdbcType.OTHER, typeHandler = PostgresJsonbTypeHandler.class)
    private Map<String, Object> metadata;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
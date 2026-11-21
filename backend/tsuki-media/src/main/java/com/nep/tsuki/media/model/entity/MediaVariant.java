package com.nep.tsuki.media.model.entity;

import java.time.OffsetDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("media_variants")
public class MediaVariant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long assetId;
    private String code;
    private String storageSpace;
    private String objectKey;
    private String contentType;
    private Long byteSize;
    private Integer width;
    private Integer height;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}

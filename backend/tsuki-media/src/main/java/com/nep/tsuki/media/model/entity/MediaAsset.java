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
@TableName("media_assets")
public class MediaAsset {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long uploaderAccountId;
    private String kind;
    private String status;
    private String objectKey;
    private String contentType;
    private Long byteSize;
    private String etag;
    private OffsetDateTime confirmExpiresAt;
    private OffsetDateTime confirmedAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}

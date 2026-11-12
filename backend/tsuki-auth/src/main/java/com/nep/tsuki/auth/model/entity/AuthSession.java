package com.nep.tsuki.auth.model.entity;

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
@TableName("auth_sessions")
public class AuthSession {

    @TableId(type = IdType.INPUT)
    private String id;

    private Long accountId;
    private String currentRefreshJti;
    private OffsetDateTime expiresAt;
    private OffsetDateTime absoluteExpiresAt;
    private OffsetDateTime revokedAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}

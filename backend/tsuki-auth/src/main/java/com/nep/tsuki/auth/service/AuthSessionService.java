package com.nep.tsuki.auth.service;

import java.time.Instant;
import java.time.OffsetDateTime;

import com.nep.tsuki.auth.model.entity.AuthSession;

public interface AuthSessionService {
    /**
     * 创建认证会话
     * 
     * @param sid          会话ID
     * @param accountId    账号ID
     * @param jti          刷新令牌ID
     * @param expiresAt    刷新令牌过期时间
     * @param absExpiresAt 刷新令牌绝对过期时间
     */
    void create(String sid, Long accountId, String jti, Instant expiresAt, Instant absExpiresAt);

    /**
     * 撤销认证会话
     * 
     * @param authSession 认证会话
     * @param now         当前时间
     */
    void revoke(AuthSession authSession, OffsetDateTime now);

    /**
     * 更新认证会话
     * 
     * @param authSession 认证会话
     * @param newJti      新的刷新令牌ID
     * @param expiresAt   刷新令牌过期时间
     * @param now         当前时间
     */
    void update(AuthSession authSession, String newJti, Instant expiresAt, OffsetDateTime now);
}

package com.nep.tsuki.auth.service.impl;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;

import com.nep.tsuki.auth.mapper.AuthSessionMapper;
import com.nep.tsuki.auth.model.entity.AuthSession;
import com.nep.tsuki.auth.service.AuthSessionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthSessionServiceImpl implements AuthSessionService {
    private final AuthSessionMapper authSessionMapper;

    /**
     * 创建认证会话
     * 
     * @param sid          会话ID
     * @param accountId    账号ID
     * @param jti          刷新令牌ID
     * @param expiresAt    刷新令牌过期时间
     * @param absExpiresAt 刷新令牌绝对过期时间
     */
    @Override
    public void create(String sid, Long accountId, String jti, Instant expiresAt, Instant absExpiresAt) {
        AuthSession authSession = new AuthSession();
        authSession.setId(sid);
        authSession.setAccountId(accountId);
        authSession.setCurrentRefreshJti(jti);
        authSession.setExpiresAt(OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
        authSession.setAbsoluteExpiresAt(OffsetDateTime.ofInstant(absExpiresAt, ZoneOffset.UTC));
        authSessionMapper.insert(authSession);
        log.info("创建认证会话成功, expiresAt={}", expiresAt);
    }

    /**
     * 撤销认证会话
     * 
     * @param authSession 认证会话
     * @param now         当前时间
     */
    @Override
    public void revoke(AuthSession authSession, OffsetDateTime now) {
        authSession.setRevokedAt(now);
        authSession.setUpdatedAt(now);
        authSessionMapper.updateById(authSession);
    }

    /**
     * 更新认证会话
     * 
     * @param authSession 认证会话
     * @param newJti      新的刷新令牌ID
     * @param expiresAt   刷新令牌过期时间
     * @param now         当前时间
     */
    @Override
    public void update(AuthSession authSession, String newJti, Instant expiresAt, OffsetDateTime now) {
        authSession.setCurrentRefreshJti(newJti);
        authSession.setExpiresAt(OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
        authSession.setUpdatedAt(now);
        authSessionMapper.updateById(authSession);
    }
}

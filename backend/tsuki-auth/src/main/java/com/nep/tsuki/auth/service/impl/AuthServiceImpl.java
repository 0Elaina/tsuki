package com.nep.tsuki.auth.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.nep.tsuki.api.ApiCode;
import com.nep.tsuki.auth.config.JwtProperties;
import com.nep.tsuki.auth.constants.AuthApiCode;
import com.nep.tsuki.auth.constants.JwtConstants;
import com.nep.tsuki.auth.mapper.AuthSessionMapper;
import com.nep.tsuki.auth.model.dto.AuthenticatedAccount;
import com.nep.tsuki.auth.model.dto.IssuedTokens;
import com.nep.tsuki.auth.model.entity.AuthSession;
import com.nep.tsuki.auth.model.enums.AccountType;
import com.nep.tsuki.auth.service.AccountAuthenticateService;
import com.nep.tsuki.auth.service.AuthService;
import com.nep.tsuki.auth.service.AuthSessionService;
import com.nep.tsuki.auth.service.LoginRateLimitService;
import com.nep.tsuki.auth.service.TokenService;
import com.nep.tsuki.exceptions.BusinessException;
import com.nep.tsuki.utils.TimeUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {
    private final TokenService tokenService;
    private final JwtProperties jwtProperties;
    private final AccountAuthenticateService accountAuthenticateService;
    private final LoginRateLimitService loginRateLimitService;
    private final JwtDecoder refreshJwtDecoder;
    private final AuthSessionService authSessionService;
    private final AuthSessionMapper authSessionMapper;

    public AuthServiceImpl(TokenService tokenService, JwtProperties jwtProperties,
            AccountAuthenticateService accountAuthenticateService,
            LoginRateLimitService loginRateLimitService,
            @Qualifier("refreshJwtDecoder") JwtDecoder refreshJwtDecoder,
            AuthSessionService authSessionService,
            AuthSessionMapper authSessionMapper) {
        this.tokenService = tokenService;
        this.jwtProperties = jwtProperties;
        this.accountAuthenticateService = accountAuthenticateService;
        this.loginRateLimitService = loginRateLimitService;
        this.refreshJwtDecoder = refreshJwtDecoder;
        this.authSessionService = authSessionService;
        this.authSessionMapper = authSessionMapper;
    }

    /**
     * 登录
     * 
     * @param username    用户名
     * @param password    密码
     * @param clientIp    客户端ip
     * @param accountType 账户类型
     * @return 签发的令牌
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public IssuedTokens login(String username, String password, String clientIp, AccountType accountType) {
        loginRateLimitService.checkAndConsume(accountType, clientIp, username);
        AuthenticatedAccount authenticatedAccount = accountAuthenticateService.authenticate(username, password);
        if (authenticatedAccount.accountType() != accountType) {
            throw new BusinessException(ApiCode.ACCESS_DENIED);
        }

        String sid = UUID.randomUUID().toString();
        String jti = UUID.randomUUID().toString();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        Instant absExpiresAt = accountType == AccountType.ADMIN
                ? now.plus(jwtProperties.refreshAdminAbsTtl())
                : now.plus(jwtProperties.refreshUserAbsTtl());
        Duration refreshTtl = accountType == AccountType.ADMIN
                ? jwtProperties.refreshAdminIdleTtl()
                : jwtProperties.refreshUserIdleTtl();
        Instant refreshExpiresAt = TimeUtils.minInstant(now.plus(refreshTtl), absExpiresAt);

        IssuedTokens issuedTokens = tokenService.issue(accountType, authenticatedAccount.accountId(), sid, jti,
                refreshExpiresAt, now);
        authSessionService.create(sid, authenticatedAccount.accountId(), jti, refreshExpiresAt, absExpiresAt);
        log.info("用户 {} 登录成功", username);
        return issuedTokens;

    }

    /**
     * 刷新令牌
     * 
     * @param accountType  账户类型
     * @param refreshToken 刷新令牌
     * @return 签发的令牌
     */
    @Override
    @Transactional(rollbackFor = Exception.class, noRollbackFor = BusinessException.class)
    public IssuedTokens refresh(AccountType accountType, String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException(AuthApiCode.INVALID_REFRESH_TOKEN);
        }

        // 解析旧刷新令牌
        Jwt jwt;
        try {
            jwt = refreshJwtDecoder.decode(refreshToken);
        } catch (JwtException e) {
            throw new BusinessException(AuthApiCode.INVALID_REFRESH_TOKEN, "令牌已过期或无效");
        }

        // 查询会话并加锁
        AuthSession authSession = authSessionMapper.selectByIdForUpdate(jwt.getClaimAsString(JwtConstants.SID));

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        OffsetDateTime nowUtc = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);

        Long accountId = Long.parseLong(jwt.getSubject());

        if (authSession == null || accountId == null || !accountId.equals(authSession.getAccountId())
                || authSession.getRevokedAt() != null || !now.isBefore(authSession.getExpiresAt().toInstant())
                || !now.isBefore(authSession.getAbsoluteExpiresAt().toInstant())
                || !accountType.name().equals(jwt.getClaimAsString(JwtConstants.ACCOUNT_TYPE))) {
            throw new BusinessException(AuthApiCode.INVALID_REFRESH_TOKEN);
        }

        // 重放检查
        if (!jwt.getId().equals(authSession.getCurrentRefreshJti())) {
            authSessionService.revoke(authSession, nowUtc);
            throw new BusinessException(AuthApiCode.INVALID_REFRESH_TOKEN);
        }

        // 账号复查
        AuthenticatedAccount authenticatedAccount = accountAuthenticateService.findActiveById(accountId);
        if (authenticatedAccount == null || !authenticatedAccount.accountType().equals(accountType)) {
            authSessionService.revoke(authSession, nowUtc);
            throw new BusinessException(AuthApiCode.INVALID_REFRESH_TOKEN);
        }

        // 轮换签发令牌
        String newJti = UUID.randomUUID().toString();
        Duration refreshTtl = accountType == AccountType.ADMIN
                ? jwtProperties.refreshAdminIdleTtl()
                : jwtProperties.refreshUserIdleTtl();
        Instant refreshExpiresAt = TimeUtils.minInstant(now.plus(refreshTtl),
                authSession.getAbsoluteExpiresAt().toInstant());
        IssuedTokens issuedTokens = tokenService.issue(accountType, accountId, authSession.getId(), newJti,
                refreshExpiresAt, now);

        // 更新会话
        authSessionService.update(authSession, newJti, refreshExpiresAt, nowUtc);
        log.info("用户 {} 刷新令牌成功, expiresAt={}", accountId, refreshExpiresAt);
        return issuedTokens;
    }

    /**
     * 登出
     * 
     * @param accountType  账户类型
     * @param refreshToken 刷新令牌
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logout(AccountType accountType, String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            log.debug("令牌为空, 幂等跳过");
            return;
        }

        OffsetDateTime now = OffsetDateTime.ofInstant(Instant.now().truncatedTo(ChronoUnit.SECONDS), ZoneOffset.UTC);
        Jwt jwt;
        try {
            jwt = refreshJwtDecoder.decode(refreshToken);
        } catch (JwtException e) {
            log.debug("令牌已过期或非法, 幂等跳过");
            return;
        }

        if (!accountType.name().equals(jwt.getClaimAsString(JwtConstants.ACCOUNT_TYPE))) {
            log.debug("账号类型不匹配, 幂等跳过");
            return;
        }

        String sid = jwt.getClaimAsString(JwtConstants.SID);
        AuthSession authSession = authSessionMapper.selectByIdForUpdate(sid);
        if (authSession == null || authSession.getRevokedAt() != null) {
            log.debug("会话不存在或已注销, 幂等跳过");
            return;
        }

        authSessionService.revoke(authSession, now);
        log.info("用户 {} 登出成功", jwt.getSubject());
    }

}

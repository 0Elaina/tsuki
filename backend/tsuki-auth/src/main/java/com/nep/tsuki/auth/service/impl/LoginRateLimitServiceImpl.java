package com.nep.tsuki.auth.service.impl;

import java.util.List;
import java.util.Locale;

import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.nep.tsuki.auth.constants.AuthApiCode;
import com.nep.tsuki.auth.constants.AuthRedisConstants;
import com.nep.tsuki.auth.model.enums.AccountType;
import com.nep.tsuki.auth.service.LoginRateLimitService;
import com.nep.tsuki.exceptions.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginRateLimitServiceImpl implements LoginRateLimitService {
    private final StringRedisTemplate stringRedisTemplate;
    private static final RedisScript<Long> RATE_LIMIT_SCRIPT = RedisScript
            .of(new ClassPathResource("lua/login_rate_limit.lua"), Long.class);

    /**
     * 检查并消耗登录次数
     * 
     * @param accountType 账户类型
     * @param clientIp    客户端IP
     * @param username    用户名
     */
    @Override
    public void checkAndConsume(AccountType accountType, String clientIp, String username) {
        if (accountType == null || !StringUtils.hasText(clientIp) || !StringUtils.hasText(username)) {
            throw new IllegalArgumentException("账号类型、IP地址和用户名不能为空");
        }
        String keyPrefix = AuthRedisConstants.LOGIN_KEY_PREFIX + accountType.name().toLowerCase(Locale.ROOT);
        consumeQuota(keyPrefix + AuthRedisConstants.IP_KEY_SEGMENT + clientIp, AuthRedisConstants.IP_MAX_ATTEMPTS);
        consumeQuota(keyPrefix + AuthRedisConstants.USERNAME_KEY_SEGMENT + username,
                AuthRedisConstants.USERNAME_MAX_ATTEMPTS);
    }

    private void consumeQuota(String key, long maxAttempts) {
        try {
            Long remainingMillis = stringRedisTemplate.execute(RATE_LIMIT_SCRIPT, List.of(key),
                    String.valueOf(maxAttempts), String.valueOf(AuthRedisConstants.LOGIN_WINDOW_MILLIS));
            if (remainingMillis == null) {
                throw new BusinessException(AuthApiCode.LOGIN_UNAVAILABLE);
            }
            if (remainingMillis > 0L) {
                long retryAfterSeconds = (remainingMillis + 999L) / 1000L;
                throw new BusinessException(AuthApiCode.LOGIN_RATE_LIMITED, "登录过于频繁, 请" + retryAfterSeconds + "秒后重试");
            }
        } catch (DataAccessException e) {
            log.error("执行Lua脚本失败", e);
            throw new BusinessException(AuthApiCode.LOGIN_UNAVAILABLE);
        }
    }
}

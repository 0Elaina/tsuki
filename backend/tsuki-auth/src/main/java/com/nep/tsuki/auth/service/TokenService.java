package com.nep.tsuki.auth.service;

import java.time.Instant;

import com.nep.tsuki.auth.model.dto.IssuedTokens;
import com.nep.tsuki.auth.model.enums.AccountType;

public interface TokenService {
    /**
     * 签发双令牌
     * 
     * @param accountType      账号类型
     * @param accountId        账号ID
     * @param sid              会话ID
     * @param refreshJti       刷新令牌JTI
     * @param refreshExpiresAt 刷新令牌过期时间
     * @param now              当前时间
     * @return 签发的令牌
     */
    IssuedTokens issue(AccountType accountType, Long accountId, String sid, String refreshJti,
            Instant refreshExpiresAt, Instant now);
}

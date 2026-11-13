package com.nep.tsuki.auth.service;

import com.nep.tsuki.auth.model.dto.IssuedTokens;
import com.nep.tsuki.auth.model.enums.AccountType;

public interface AuthService {
    /**
     * 登录
     * 
     * @param username    用户名
     * @param password    密码
     * @param clientIp    客户端ip
     * @param accountType 账户类型
     * @return 签发的令牌
     */
    IssuedTokens login(String username, String password, String clientIp, AccountType accountType);

    /**
     * 刷新令牌
     * 
     * @param accountType  账户类型
     * @param refreshToken 刷新令牌
     * @return 签发的令牌
     */
    IssuedTokens refresh(AccountType accountType, String refreshToken);

    /**
     * 登出
     * 
     * @param accountType  账户类型
     * @param refreshToken 刷新令牌
     */
    void logout(AccountType accountType, String refreshToken);
}

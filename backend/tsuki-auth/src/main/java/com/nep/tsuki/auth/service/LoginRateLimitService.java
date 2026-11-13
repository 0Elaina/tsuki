package com.nep.tsuki.auth.service;

import com.nep.tsuki.auth.model.enums.AccountType;

public interface LoginRateLimitService {
    /**
     * 检查并消耗登录次数
     * 
     * @param accountType 账户类型
     * @param clientIp    客户端IP
     * @param username    用户名
     */
    void checkAndConsume(AccountType accountType, String clientIp, String username);
}

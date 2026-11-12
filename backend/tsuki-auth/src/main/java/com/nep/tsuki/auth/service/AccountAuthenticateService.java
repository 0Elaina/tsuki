package com.nep.tsuki.auth.service;

import com.nep.tsuki.auth.model.dto.AuthenticatedAccount;

public interface AccountAuthenticateService {
    /**
     * 账号认证
     * 
     * @param username 用户名
     * @param password 密码
     * @return 认证后的账号信息
     */
    AuthenticatedAccount authenticate(String username, String password);
}

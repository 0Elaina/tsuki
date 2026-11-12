package com.nep.tsuki.auth.service;

public interface LoginRateLimitService {
    /**
     * 检查并消耗登录次数
     * 
     * @param clientIp 客户端IP
     * @param username 用户名
     */
    void checkAndConsume(String clientIp, String username);
}

package com.nep.tsuki.user.service.impl;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nep.tsuki.auth.model.dto.AuthenticatedAccount;
import com.nep.tsuki.auth.service.AccountAuthenticateService;
import com.nep.tsuki.exceptions.BusinessException;
import com.nep.tsuki.user.constants.AccountApiCode;
import com.nep.tsuki.user.mapper.UserAccountMapper;
import com.nep.tsuki.user.model.entity.UserAccount;
import com.nep.tsuki.user.model.enums.AccountStatus;
import com.nep.tsuki.user.utils.AccountTypeUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AccountAuthenticateServiceImpl implements AccountAuthenticateService {
    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final String dummyHash;

    public AccountAuthenticateServiceImpl(UserAccountMapper userAccountMapper, PasswordEncoder passwordEncoder) {
        this.userAccountMapper = userAccountMapper;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode("dummy");
    }

    /**
     * 账号认证
     * 
     * @param username 用户名
     * @param password 密码
     * @return 认证后的账号信息
     */
    @Override
    public AuthenticatedAccount authenticate(String username, String password) {
        UserAccount userAccount = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, username));
        if (userAccount == null) {
            passwordEncoder.matches(password, dummyHash);
            throw new BusinessException(AccountApiCode.INVALID_CREDENTIALS);
        }

        if (!passwordEncoder.matches(password, userAccount.getPasswordHash())) {
            throw new BusinessException(AccountApiCode.INVALID_CREDENTIALS);
        }

        if (!AccountStatus.ACTIVE.name().equals(userAccount.getStatus())) {
            throw new BusinessException(AccountApiCode.ACCOUNT_DISABLED);
        }
        return new AuthenticatedAccount(
                userAccount.getId(), AccountTypeUtils.getAccountType(userAccount.getRole()));
    }

    /**
     * 获取已激活的账号信息
     * 
     * @param accountId 账号ID
     * @return 账号信息
     */
    @Override
    public AuthenticatedAccount findActiveById(Long accountId) {
        UserAccount userAccount = userAccountMapper.selectById(accountId);
        if (userAccount == null || !AccountStatus.ACTIVE.name().equals(userAccount.getStatus())) {
            // 返回空, 交给调用方撤销会话
            return null;
        }
        return new AuthenticatedAccount(
                userAccount.getId(), AccountTypeUtils.getAccountType(userAccount.getRole()));
    }
}

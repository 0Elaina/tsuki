package com.nep.tsuki.user.service.impl;

import org.springframework.stereotype.Service;

import com.nep.tsuki.exceptions.BusinessException;
import com.nep.tsuki.user.constants.AccountApiCode;
import com.nep.tsuki.user.mapper.UserAccountMapper;
import com.nep.tsuki.user.model.entity.UserAccount;
import com.nep.tsuki.user.model.vo.AccountIdentityResponse;
import com.nep.tsuki.user.service.AccountService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {
    private final UserAccountMapper userAccountMapper;

    /**
     * 根据id查询账户信息
     *
     * @param id 账户id
     * @return 账户信息
     */
    @Override
    public AccountIdentityResponse findById(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(AccountApiCode.ACCOUNT_NOT_FOUND);
        }
        UserAccount userAccount = userAccountMapper.selectById(id);
        if (userAccount == null) {
            throw new BusinessException(AccountApiCode.ACCOUNT_NOT_FOUND);
        }
        return AccountIdentityResponse.of(userAccount);
    }

}

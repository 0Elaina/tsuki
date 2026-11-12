package com.nep.tsuki.user.service;

import com.nep.tsuki.user.model.vo.AccountIdentityResponse;

public interface AccountService {
    /**
     * 根据id查询账户信息
     *
     * @param id 账户id
     * @return 账户信息
     */
    AccountIdentityResponse findById(Long id);
}

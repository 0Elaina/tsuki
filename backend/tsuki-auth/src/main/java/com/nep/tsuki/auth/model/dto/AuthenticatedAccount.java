package com.nep.tsuki.auth.model.dto;

import com.nep.tsuki.common.enums.AccountType;

public record AuthenticatedAccount(
        Long accountId,
        AccountType accountType) {

}

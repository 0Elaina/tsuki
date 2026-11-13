package com.nep.tsuki.user.utils;

import java.util.List;

import com.nep.tsuki.auth.model.enums.AccountType;
import com.nep.tsuki.user.model.enums.AccountRole;

public final class AccountTypeUtils {
    private AccountTypeUtils() {
    }

    public static final List<AccountRole> ADMIN_ROLES = List.of(AccountRole.ADMIN);
    public static final List<AccountRole> USER_ROLES = List.of(AccountRole.USER);

    public static AccountType getAccountType(AccountRole role) {
        if (ADMIN_ROLES.contains(role)) {
            return AccountType.ADMIN;
        }
        return AccountType.USER;
    }

    public static AccountType getAccountType(String role) {
        return getAccountType(AccountRole.valueOf(role));
    }
}

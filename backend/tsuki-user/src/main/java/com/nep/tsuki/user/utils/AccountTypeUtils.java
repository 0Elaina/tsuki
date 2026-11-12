package com.nep.tsuki.user.utils;

import java.util.List;

import com.nep.tsuki.auth.model.enums.AccountType;
import com.nep.tsuki.user.model.enums.AccountRole;

public final class AccountTypeUtils {
    private AccountTypeUtils() {
    }

    public static List<AccountRole> getAdminRoles() {
        return List.of(AccountRole.ADMIN);
    }

    public static List<AccountRole> getUserRoles() {
        return List.of(AccountRole.USER);
    }

    public static AccountType getAccountType(AccountRole role) {
        if (getAdminRoles().contains(role)) {
            return AccountType.ADMIN;
        }
        return AccountType.USER;
    }

    public static AccountType getAccountType(String role) {
        return getAccountType(AccountRole.valueOf(role));
    }
}

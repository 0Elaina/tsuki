package com.nep.tsuki.user.constants;

public final class AccountValidationConstants {
    private AccountValidationConstants() {

    }

    public static final String USERNAME_REGEX = "\\A[A-Za-z][A-Za-z0-9_]{2,31}\\z";
    public static final String PASSWORD_REGEX = "\\A[!-~]{8,72}\\z";
}

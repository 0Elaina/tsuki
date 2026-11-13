package com.nep.tsuki.auth.constants;

public final class CookieConstants {
    private CookieConstants() {
    }

    public static final String ADMIN_RT_COOKIE_NAME = "tsuki_admin_refresh";
    public static final String ADMIN_RT_COOKIE_PATH = "/api/v1/admin/auth";
    public static final String USER_RT_COOKIE_NAME = "tsuki_user_refresh";
    public static final String USER_RT_COOKIE_PATH = "/api/v1/auth";

}

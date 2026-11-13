package com.nep.tsuki.auth.constants;

public final class AuthRedisConstants {
    private AuthRedisConstants() {
    }

    public static final String AUTH_PREFIX = "tsuki:auth:";
    public static final String LOGIN_KEY_PREFIX = AUTH_PREFIX + "login:";
    public static final String IP_KEY_SEGMENT = ":ip:";
    public static final String USERNAME_KEY_SEGMENT = ":username:";

    public static final long LOGIN_WINDOW_MILLIS = 60_000L;
    public static final long IP_MAX_ATTEMPTS = 30L;
    public static final long USERNAME_MAX_ATTEMPTS = 10L;
}

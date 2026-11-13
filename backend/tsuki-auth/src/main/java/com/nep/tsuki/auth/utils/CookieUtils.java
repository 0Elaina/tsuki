package com.nep.tsuki.auth.utils;

import java.time.Duration;

import org.springframework.boot.web.server.Cookie.SameSite;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import com.nep.tsuki.constants.EnvConstants;

import jakarta.servlet.http.HttpServletResponse;

public final class CookieUtils {
    private CookieUtils() {
    }

    public static boolean isCookieSecure(Environment environment) {
        boolean devOnly = environment.acceptsProfiles(Profiles.of(EnvConstants.DEV))
                && !environment.acceptsProfiles(Profiles.of(EnvConstants.PROD));
        return !devOnly;
    }

    public static ResponseCookie setCookie(HttpServletResponse response, String name, String value, String path,
            Duration maxAge,
            Environment environment) {
        ResponseCookie cookie = ResponseCookie
                .from(name, value)
                .httpOnly(true)
                .secure(isCookieSecure(environment))
                .path(path)
                .maxAge(maxAge)
                .sameSite(SameSite.STRICT.attributeValue())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return cookie;
    }

    public static ResponseCookie clearCookie(HttpServletResponse response, String name, String path,
            Environment environment) {
        return setCookie(response, name, "", path, Duration.ZERO, environment);
    }
}

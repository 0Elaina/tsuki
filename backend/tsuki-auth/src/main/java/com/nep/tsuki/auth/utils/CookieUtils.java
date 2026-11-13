package com.nep.tsuki.auth.utils;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import com.nep.tsuki.constants.EnvConstants;

public final class CookieUtils {
    private CookieUtils() {
    }

    public static boolean isCookieSecure(Environment environment) {
        boolean devOnly = environment.acceptsProfiles(Profiles.of(EnvConstants.DEV))
                && !environment.acceptsProfiles(Profiles.of(EnvConstants.PROD));
        return !devOnly;
    }
}

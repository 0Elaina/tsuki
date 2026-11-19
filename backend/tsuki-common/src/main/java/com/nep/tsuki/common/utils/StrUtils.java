package com.nep.tsuki.common.utils;

import org.springframework.util.StringUtils;

public final class StrUtils {
    private StrUtils() {

    }

    public static String stripToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }
}

package com.nep.tsuki.utils;

import org.springframework.util.StringUtils;

public final class SqlUtils {
    private SqlUtils() {
    }

    public static String escapeLike(String keyword) {
        if (!StringUtils.hasText(keyword))
            return "";
        return keyword.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    public static String toContainsLikePattern(String keyword) {
        return "%" + escapeLike(keyword) + "%";
    }
}

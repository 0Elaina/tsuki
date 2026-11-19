package com.nep.tsuki.common.utils;

import java.time.Instant;

public final class TimeUtils {
    private TimeUtils() {
    }

    public static Instant minInstant(Instant a, Instant b) {
        return !a.isAfter(b) ? a : b;
    }
}

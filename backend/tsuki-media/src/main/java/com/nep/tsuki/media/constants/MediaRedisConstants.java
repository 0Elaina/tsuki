package com.nep.tsuki.media.constants;

public final class MediaRedisConstants {
    private MediaRedisConstants() {
    }

    public static final String UPLOAD_SIGN_KEY_PREFIX = "tsuki:media:upload:sign:";
    public static final long UPLOAD_SIGN_WINDOW_MILLIS = 60_000L;
}

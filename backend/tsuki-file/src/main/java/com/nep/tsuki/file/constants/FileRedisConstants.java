package com.nep.tsuki.file.constants;

public final class FileRedisConstants {
    private FileRedisConstants() {
    }

    public static final String UPLOAD_SIGN_KEY_PREFIX = "tsuki:file:upload:sign:";
    public static final long UPLOAD_SIGN_WINDOW_MILLIS = 60_000L;
}

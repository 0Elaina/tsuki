package com.nep.tsuki.common.constants;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class TimeConstants {
    private TimeConstants() {
    }

    public static final String ZONE_SHANGHAI = "Asia/Shanghai";

    /** 斜杠分隔日期格式（如 2026/11/25），常用于对象存储路径分区 */
    public static final DateTimeFormatter DATE_SLASH = DateTimeFormatter.ofPattern("uuuu/MM/dd", Locale.ROOT);

    /** 标准 ISO 日期格式（如 2026-11-25） */
    public static final DateTimeFormatter DATE_DASH = DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ROOT);
}

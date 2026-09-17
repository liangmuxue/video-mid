package com.jizhi.videomid.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** 毫秒时间戳与 LocalDateTime 互转（系统默认时区）。 */
public final class TsUtil {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private TsUtil() {
    }

    public static Long toMillis(LocalDateTime dt) {
        if (dt == null) {
            return null;
        }
        return dt.atZone(ZONE).toInstant().toEpochMilli();
    }

    public static LocalDateTime fromMillis(Long millis) {
        if (millis == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZONE);
    }

    public static long nowMillis() {
        return System.currentTimeMillis();
    }
}

package com.wordflip.util;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 解析 X-Timezone header 为用户「当日」日历日（api-modules §4）。
 */
public final class UserTimeZoneUtil {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    private UserTimeZoneUtil() {
    }

    public static ZoneId resolveZone(String timezoneHeader) {
        if (timezoneHeader == null || timezoneHeader.isBlank()) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(timezoneHeader.trim());
        } catch (Exception ex) {
            return DEFAULT_ZONE;
        }
    }

    public static LocalDate todayInZone(ZoneId zoneId) {
        return LocalDate.now(zoneId);
    }
}

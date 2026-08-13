package com.wordflip.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * 验证用户时区 header 的正常解析与安全回退。
 */
class UserTimeZoneUtilTest {

    @Test
    void resolvesValidZone() {
        assertThat(UserTimeZoneUtil.resolveZone("Europe/Paris"))
                .isEqualTo(ZoneId.of("Europe/Paris"));
    }

    @Test
    void fallsBackForInvalidZone() {
        assertThat(UserTimeZoneUtil.resolveZone("invalid/timezone"))
                .isEqualTo(ZoneId.of("Asia/Shanghai"));
    }

    @Test
    void fallsBackForBlankZone() {
        assertThat(UserTimeZoneUtil.resolveZone("   "))
                .isEqualTo(ZoneId.of("Asia/Shanghai"));
    }
}

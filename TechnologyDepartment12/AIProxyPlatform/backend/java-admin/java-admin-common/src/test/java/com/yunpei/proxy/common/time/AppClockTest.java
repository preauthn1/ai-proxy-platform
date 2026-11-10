package com.yunpei.proxy.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * {@link AppClock} 跟注入的 Clock 走。
 */
class AppClockTest {

    @Test
    void now_fixedUtcInstant_isShanghaiWallClock() {
        Instant fixed = Instant.parse("2026-08-14T08:00:00Z");
        AppClock clock = new AppClock(Clock.fixed(fixed, ZoneOffset.UTC));
        assertThat(clock.instant()).isEqualTo(fixed);
        assertThat(clock.now()).isEqualTo(LocalDateTime.of(2026, 8, 14, 16, 0, 0));
    }
}

package com.yunpei.proxy.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@link TimeZones}：UTC Instant 与上海墙钟互转。
 */
class TimeZonesTest {

    @AfterEach
    void resetClock() {
        TimeZones.resetClock();
    }

    @Test
    void platform_isShanghai() {
        assertThat(TimeZones.PLATFORM_ID).isEqualTo("Asia/Shanghai");
        assertThat(TimeZones.PLATFORM.getId()).isEqualTo("Asia/Shanghai");
    }

    @Test
    void toLocal_utcInstant_isShanghaiWallClock() {
        Instant utc = Instant.parse("2026-08-14T08:00:00Z");
        assertThat(TimeZones.toLocal(utc)).isEqualTo(LocalDateTime.of(2026, 8, 14, 16, 0, 0));
    }

    @Test
    void toInstant_shanghaiWallClock_isUtc() {
        LocalDateTime local = LocalDateTime.of(2026, 8, 14, 16, 0, 0);
        assertThat(TimeZones.toInstant(local)).isEqualTo(Instant.parse("2026-08-14T08:00:00Z"));
    }

    @Test
    void now_followsFixedClock() {
        Instant fixed = Instant.parse("2026-08-14T08:00:00Z");
        TimeZones.useClock(Clock.fixed(fixed, ZoneOffset.UTC));
        assertThat(TimeZones.now()).isEqualTo(LocalDateTime.of(2026, 8, 14, 16, 0, 0));
        assertThat(TimeZones.instant()).isEqualTo(fixed);
    }

    @Test
    void toLocal_null_isNull() {
        assertThat(TimeZones.toLocal(null)).isNull();
        assertThat(TimeZones.toInstant(null)).isNull();
    }
}

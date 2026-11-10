package com.yunpei.proxy.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * {@link DateTimes}：线格式与解析。
 */
class DateTimesTest {

    private static final LocalDateTime SHANGHAI_16 = LocalDateTime.of(2026, 8, 14, 16, 0, 0);

    @Test
    void formatOffset_appendsPlus0800() {
        assertThat(DateTimes.formatOffset(SHANGHAI_16)).isEqualTo("2026-08-14T16:00:00+08:00");
        assertThat(DateTimes.formatOffset(null)).isNull();
    }

    @Test
    void parse_zulu_toShanghai() {
        assertThat(DateTimes.parse("2026-08-14T08:00:00Z")).isEqualTo(SHANGHAI_16);
    }

    @Test
    void parse_offset_toShanghai() {
        assertThat(DateTimes.parse("2026-08-14T16:00:00+08:00")).isEqualTo(SHANGHAI_16);
        assertThat(DateTimes.parse("2026-08-14T04:00:00-04:00")).isEqualTo(SHANGHAI_16);
    }

    @Test
    void parse_naive_isShanghaiWallClock() {
        assertThat(DateTimes.parse("2026-08-14T16:00:00")).isEqualTo(SHANGHAI_16);
        assertThat(DateTimes.parse("2026-08-14 16:00:00")).isEqualTo(SHANGHAI_16);
    }

    @Test
    void parse_blankOrGarbage_isNull() {
        assertThat(DateTimes.parse(null)).isNull();
        assertThat(DateTimes.parse("  ")).isNull();
        assertThat(DateTimes.parse("not-a-date")).isNull();
    }
}

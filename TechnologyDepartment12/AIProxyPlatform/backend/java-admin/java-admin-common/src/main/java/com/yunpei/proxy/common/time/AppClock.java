package com.yunpei.proxy.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 可注入的平台时钟。默认跟 {@link TimeZones} 走同一只钟。
 *
 * @author yunpei
 */
public final class AppClock {

    private final Clock clock;

    /** 使用 {@link TimeZones#clock()}。 */
    public AppClock() {
        this(TimeZones.clock());
    }

    /**
     * @param clock 非空时钟
     */
    public AppClock(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 物理时刻。
     *
     * @return Instant
     */
    public Instant instant() {
        return clock.instant();
    }

    /**
     * 上海墙钟。
     *
     * @return LocalDateTime
     */
    public LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), TimeZones.PLATFORM);
    }
}

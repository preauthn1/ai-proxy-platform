package com.yunpei.proxy.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 平台时区与「现在」。
 *
 * <p>墙钟一律 {@link #PLATFORM}（Asia/Shanghai），禁止 {@code ZoneId.systemDefault()}。
 *
 * @author yunpei
 */
public final class TimeZones {

    /** 平台墙钟 IANA id。 */
    public static final String PLATFORM_ID = "Asia/Shanghai";

    /** 平台墙钟。 */
    public static final ZoneId PLATFORM = ZoneId.of(PLATFORM_ID);

    private static final AtomicReference<Clock> CLOCK = new AtomicReference<>(Clock.system(PLATFORM));

    /**
     * 当前平台墙钟。
     *
     * @return 上海墙钟的 LocalDateTime
     */
    public static LocalDateTime now() {
        return LocalDateTime.ofInstant(instant(), PLATFORM);
    }

    /**
     * 当前物理时刻。
     *
     * @return Instant
     */
    public static Instant instant() {
        return CLOCK.get().instant();
    }

    /**
     * Instant → 上海墙钟；null 保持 null。
     *
     * @param instant 物理时刻
     * @return 上海墙钟，或 null
     */
    public static LocalDateTime toLocal(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, PLATFORM);
    }

    /**
     * 上海墙钟 → Instant；null 保持 null。
     *
     * @param local 上海墙钟
     * @return Instant，或 null
     */
    public static Instant toInstant(LocalDateTime local) {
        return local == null ? null : local.atZone(PLATFORM).toInstant();
    }

    /**
     * 当前时钟（测试可替换）。
     *
     * @return Clock
     */
    public static Clock clock() {
        return CLOCK.get();
    }

    /**
     * 仅测试：固定时钟。
     *
     * @param clock 非空时钟
     */
    public static void useClock(Clock clock) {
        CLOCK.set(Objects.requireNonNull(clock, "clock"));
    }

    /** 仅测试：恢复系统时钟。 */
    public static void resetClock() {
        CLOCK.set(Clock.system(PLATFORM));
    }

    private TimeZones() {}
}

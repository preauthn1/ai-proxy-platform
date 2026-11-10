package com.yunpei.proxy.common.time;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * 平台墙钟的线格式：写出带 {@code +08:00}；解析 Z / offset / 无 offset。
 *
 * @author yunpei
 */
public final class DateTimes {

    private static final DateTimeFormatter SPACE_LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Pattern OFFSET_SUFFIX = Pattern.compile("[+-]\\d{2}:\\d{2}$");

    /**
     * 上海墙钟 → {@code 2026-08-14T16:00:00+08:00}；null → null。
     *
     * @param local 上海墙钟
     * @return 带 offset 的 ISO-8601，或 null
     */
    public static String formatOffset(LocalDateTime local) {
        return local == null ? null : local.atZone(TimeZones.PLATFORM).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /**
     * 解析查询/入参时间。无法解析返回 null（筛选条件忽略）。
     *
     * <p>有 offset 或 {@code Z} 按 Instant 再转到上海墙钟；无 offset 视为上海墙钟。
     *
     * @param raw 原始字符串
     * @return 上海墙钟，或 null
     */
    public static LocalDateTime parse(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        String normalized = value.contains(" ") && !value.contains("T") ? value.replace(' ', 'T') : value;
        try {
            if (hasExplicitOffset(normalized)) {
                return TimeZones.toLocal(OffsetDateTime.parse(normalized).toInstant());
            }
            return LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ex) {
            try {
                return LocalDateTime.parse(value, SPACE_LOCAL);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    /**
     * 是否带显式 offset / {@code Z}。
     *
     * @param value 已 trim 的字符串
     * @return true=按 Instant 解析
     */
    public static boolean hasExplicitOffset(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return value.endsWith("Z")
                || value.endsWith("z")
                || OFFSET_SUFFIX.matcher(value).find();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private DateTimes() {}
}

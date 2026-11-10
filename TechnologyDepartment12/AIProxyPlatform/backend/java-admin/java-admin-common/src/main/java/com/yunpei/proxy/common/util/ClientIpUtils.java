package com.yunpei.proxy.common.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 客户端真实 IP 解析工具（对齐多级代理场景下的生产实践）。
 *
 * <p>无 servlet 依赖：由调用方传入 {@code X-Forwarded-For} / {@code X-Real-IP} / {@code remoteAddr} 等。
 * 解析顺序：XFF（取链上第一个合法 IP）→ X-Real-IP → 其它代理头 → remoteAddr。
 *
 * <p>说明：最左 XFF 在「可信反向代理正确覆盖/追加」前提下才是客户端；生产应配合
 * Tomcat {@code server.tomcat.remoteip} 仅信任内网代理，降低伪造头风险。
 *
 * @author yunpei
 */
public final class ClientIpUtils {

    private static final String UNKNOWN = "unknown";
    private static final String LOCALHOST_IPV4 = "127.0.0.1";
    private static final String LOCALHOST_IPV6_FULL = "0:0:0:0:0:0:0:1";
    private static final String LOCALHOST_IPV6_COMPACT = "::1";

    /** IPv4 字面量（0–255 每段）。 */
    private static final Pattern IPV4 =
            Pattern.compile("^(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$");

    private ClientIpUtils() {}

    /**
     * 解析客户端 IP。
     *
     * @param xForwardedFor {@code X-Forwarded-For} 原始值（可为多 IP 逗号链）
     * @param xRealIp       {@code X-Real-IP}
     * @param remoteAddr    {@link jakarta.servlet.ServletRequest#getRemoteAddr()} 对端地址
     * @param otherHeaders  其它代理相关头（如 Proxy-Client-IP、WL-Proxy-Client-IP），按序尝试
     * @return 规范化后的 IP；均不可用时返回空串（不返回 null）
     */
    public static String resolve(String xForwardedFor, String xRealIp, String remoteAddr, String... otherHeaders) {
        String fromXff = firstValidTokenFromCsv(xForwardedFor);
        if (fromXff != null) {
            return normalize(fromXff);
        }
        if (isValidIpAddress(xRealIp)) {
            return normalize(xRealIp.trim());
        }
        if (otherHeaders != null) {
            for (String header : otherHeaders) {
                // 少数代理也会在这些头里写 CSV
                String fromOther = firstValidTokenFromCsv(header);
                if (fromOther != null) {
                    return normalize(fromOther);
                }
            }
        }
        if (remoteAddr != null && !remoteAddr.isBlank()) {
            String trimmed = remoteAddr.trim();
            if (isValidIpAddress(trimmed)) {
                return normalize(trimmed);
            }
            // remoteAddr 偶发非标准字面量时仍原样规范化回环，避免丢信息
            return normalize(trimmed);
        }
        return "";
    }

    /**
     * 校验是否为可接受的 IP 字面量（排除 null / blank / {@code unknown}；支持 IPv4 / IPv6）。
     */
    public static boolean isValidIpAddress(String ip) {
        if (ip == null) {
            return false;
        }
        String value = stripZoneId(ip.trim());
        if (value.isEmpty() || UNKNOWN.equalsIgnoreCase(value)) {
            return false;
        }
        if (IPV4.matcher(value).matches()) {
            return true;
        }
        return isLikelyIpv6Literal(value);
    }

    /** 是否为本机回环地址字面量（含 IPv6 形式）。 */
    public static boolean isLoopbackLiteral(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String value = stripZoneId(ip.trim());
        return LOCALHOST_IPV4.equals(value)
                || LOCALHOST_IPV6_COMPACT.equals(value)
                || LOCALHOST_IPV6_FULL.equals(value)
                || "0000:0000:0000:0000:0000:0000:0000:0001".equalsIgnoreCase(value);
    }

    private static String firstValidTokenFromCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return null;
        }
        int start = 0;
        int len = csv.length();
        for (int i = 0; i <= len; i++) {
            if (i == len || csv.charAt(i) == ',') {
                String token = csv.substring(start, i).trim();
                if (isValidIpAddress(token)) {
                    return token;
                }
                start = i + 1;
            }
        }
        return null;
    }

    /**
     * IPv6 回环统一为 127.0.0.1；其余 trim；去掉 zone id（{@code %eth0}）。
     */
    static String normalize(String ip) {
        if (ip == null) {
            return "";
        }
        String value = stripZoneId(ip.trim());
        if (isLoopbackLiteral(value) || LOCALHOST_IPV6_COMPACT.equalsIgnoreCase(value)) {
            return LOCALHOST_IPV4;
        }
        if (LOCALHOST_IPV6_FULL.equalsIgnoreCase(value)) {
            return LOCALHOST_IPV4;
        }
        return value;
    }

    private static String stripZoneId(String ip) {
        int pct = ip.indexOf('%');
        return pct >= 0 ? ip.substring(0, pct) : ip;
    }

    /**
     * 轻量 IPv6 字面量判定（含压缩 {@code ::}、IPv4-mapped {@code ::ffff:x.x.x.x}），不做 DNS。
     */
    private static boolean isLikelyIpv6Literal(String ip) {
        if (!ip.contains(":")) {
            return false;
        }
        String lower = ip.toLowerCase(Locale.ROOT);
        // 仅允许 hex、冒号、点（mapped IPv4）
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || c == ':' || c == '.';
            if (!ok) {
                return false;
            }
        }
        // 至少一个冒号段结构；拒绝仅 ":" 或过多 "::"
        if (lower.equals(":") || lower.startsWith(":::") || lower.endsWith(":::")) {
            return false;
        }
        int doubleColon = 0;
        int idx = 0;
        while ((idx = lower.indexOf("::", idx)) >= 0) {
            doubleColon++;
            idx += 2;
            if (doubleColon > 1) {
                return false;
            }
        }
        // 含 IPv4-mapped 时右侧须为合法 IPv4
        int lastColon = lower.lastIndexOf(':');
        if (lastColon >= 0 && lower.indexOf('.', lastColon) >= 0) {
            String v4 = lower.substring(lastColon + 1);
            if (!IPV4.matcher(v4).matches()) {
                return false;
            }
        }
        return true;
    }
}

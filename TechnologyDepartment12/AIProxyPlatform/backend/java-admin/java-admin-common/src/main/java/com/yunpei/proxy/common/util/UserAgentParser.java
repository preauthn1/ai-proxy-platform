package com.yunpei.proxy.common.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 User-Agent 解析浏览器 / 操作系统 / 设备（对齐 Go mileusna/useragent 与 mock {@code parseUserAgent}）。
 *
 * <p>供登录日志、API 日志等写入 browser_name / os_name / client_name。
 *
 * @author yunpei
 */
public final class UserAgentParser {

    private static final Pattern EDGE = Pattern.compile("Edg(?:e|A|iOS)?/([\\d.]+)");
    private static final Pattern CHROME = Pattern.compile("Chrome/([\\d.]+)");
    private static final Pattern FIREFOX = Pattern.compile("Firefox/([\\d.]+)");
    private static final Pattern SAFARI = Pattern.compile("Version/([\\d.]+).*Safari");
    private static final Pattern WINDOWS = Pattern.compile("Windows NT ([\\d.]+)");
    private static final Pattern MAC = Pattern.compile("Mac OS X ([\\d_]+)");
    private static final Pattern ANDROID = Pattern.compile("Android ([\\d.]+)");
    private static final Pattern IOS = Pattern.compile("OS ([\\d_]+) like Mac OS X");
    private static final Pattern LINUX = Pattern.compile("Linux");
    private static final Pattern ANDROID_MODEL = Pattern.compile(";\\s*([^;)]+?)\\s+Build/");

    private UserAgentParser() {}

    /**
     * 解析结果。
     *
     * @param browserName    浏览器名
     * @param browserVersion 浏览器版本
     * @param osName         操作系统名
     * @param osVersion      操作系统版本
     * @param device         设备型号（若 UA 可识别）
     * @param desktop        是否桌面端
     */
    public record Parsed(
            String browserName,
            String browserVersion,
            String osName,
            String osVersion,
            String device,
            boolean desktop) {

        public static Parsed empty() {
            return new Parsed("", "", "", "", "", false);
        }

        /**
         * 客户端展示名：优先设备型号；桌面且无设备时为 {@code PC}（对齐 Go login_logger）。
         */
        public String clientName() {
            if (device != null && !device.isBlank()) {
                return device;
            }
            if (desktop) {
                return "PC";
            }
            return "";
        }
    }

    /** 浏览器名与版本。 */
    private record BrowserInfo(String name, String version) {}

    /** 操作系统名与版本。 */
    private record OsInfo(String name, String version) {}

    public static Parsed parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return Parsed.empty();
        }
        String ua = userAgent;
        BrowserInfo browser = parseBrowser(ua);
        OsInfo os = parseOs(ua);
        String lower = ua.toLowerCase(Locale.ROOT);
        String device = detectDevice(ua, lower);
        boolean mobile = isMobile(lower, os.name());
        boolean desktop = !mobile && isDesktopOs(os.name());

        return new Parsed(
                nullToEmpty(browser.name()),
                nullToEmpty(browser.version()),
                nullToEmpty(os.name()),
                nullToEmpty(os.version()),
                nullToEmpty(device),
                desktop);
    }

    private static BrowserInfo parseBrowser(String ua) {
        Matcher edge = EDGE.matcher(ua);
        if (edge.find()) {
            return new BrowserInfo("Edge", edge.group(1));
        }
        Matcher chrome = CHROME.matcher(ua);
        if (chrome.find()) {
            return new BrowserInfo("Chrome", chrome.group(1));
        }
        Matcher firefox = FIREFOX.matcher(ua);
        if (firefox.find()) {
            return new BrowserInfo("Firefox", firefox.group(1));
        }
        Matcher safari = SAFARI.matcher(ua);
        if (safari.find()) {
            return new BrowserInfo("Safari", safari.group(1));
        }
        return new BrowserInfo("Unknown", "");
    }

    private static OsInfo parseOs(String ua) {
        Matcher win = WINDOWS.matcher(ua);
        if (win.find()) {
            String ver = win.group(1);
            String osVersion = "10.0".equals(ver) ? "10/11" : ver;
            return new OsInfo("Windows", osVersion);
        }
        Matcher mac = MAC.matcher(ua);
        if (mac.find()) {
            return new OsInfo("macOS", mac.group(1).replace('_', '.'));
        }
        Matcher android = ANDROID.matcher(ua);
        if (android.find()) {
            return new OsInfo("Android", android.group(1));
        }
        Matcher ios = IOS.matcher(ua);
        if (ios.find()) {
            return new OsInfo("iOS", ios.group(1).replace('_', '.'));
        }
        if (LINUX.matcher(ua).find()) {
            return new OsInfo("Linux", "");
        }
        return new OsInfo("", "");
    }

    private static boolean isDesktopOs(String osName) {
        return "Windows".equals(osName) || "macOS".equals(osName) || "Linux".equals(osName);
    }

    private static String detectDevice(String ua, String lower) {
        if (lower.contains("ipad")) {
            return "iPad";
        }
        if (lower.contains("iphone")) {
            return "iPhone";
        }
        if (lower.contains("ipod")) {
            return "iPod";
        }
        // Android 常见型号片段：; Pixel 7 Build/ 或 ; SM-G991B
        if (lower.contains("android")) {
            Matcher modelMatcher = ANDROID_MODEL.matcher(ua);
            if (modelMatcher.find()) {
                String model = modelMatcher.group(1).trim();
                if (!model.isEmpty() && !"wv".equalsIgnoreCase(model) && !model.equalsIgnoreCase("Linux")) {
                    return model;
                }
            }
            return "Android";
        }
        return "";
    }

    private static boolean isMobile(String lower, String osName) {
        if ("Android".equals(osName) || "iOS".equals(osName)) {
            return true;
        }
        return lower.contains("mobile")
                || lower.contains("iphone")
                || lower.contains("ipad")
                || lower.contains("android");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

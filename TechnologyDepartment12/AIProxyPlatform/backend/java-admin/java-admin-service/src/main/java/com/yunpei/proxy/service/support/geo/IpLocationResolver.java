package com.yunpei.proxy.service.support.geo;

import com.google.common.base.Splitter;
import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.xdb.LongByteArray;
import org.lionsoul.ip2region.xdb.Searcher;
import org.lionsoul.ip2region.xdb.Version;
import org.springframework.stereotype.Component;

/**
 * IP 地理位置解析（对齐 Go {@code go-common/utils/ip_util/ip2region}）。
 *
 * <p>同时加载 {@code ip2region_v4.xdb} / {@code ip2region_v6.xdb}，按地址族选择 Searcher；
 * 本机/内网返回「本机」「内网」；查询失败返回空串且不影响主流程。
 * 展示格式：{@code 国家:x|省:x|市:x|服务:x}。
 *
 * @author yunpei
 */
@Slf4j
@Component
public final class IpLocationResolver {

    private static final String XDB_V4 = "ip2region/ip2region_v4.xdb";
    private static final String XDB_V6 = "ip2region/ip2region_v6.xdb";
    private static final Splitter PIPE = Splitter.on('|');
    private static final Splitter DOT = Splitter.on('.');

    private final Searcher v4Searcher;
    private final Searcher v6Searcher;

    public IpLocationResolver() {
        this.v4Searcher = loadSearcher(Version.IPv4, XDB_V4);
        this.v6Searcher = loadSearcher(Version.IPv6, XDB_V6);
    }

    /** 测试/自定义注入用（searcher 可为 null，仅走本机/内网启发式）。 */
    public IpLocationResolver(Searcher v4Searcher, Searcher v6Searcher) {
        this.v4Searcher = v4Searcher;
        this.v6Searcher = v6Searcher;
    }

    /**
     * 解析 IP 对应展示用地理位置文案。
     *
     * @param ip 客户端 IP（IPv4 / IPv6）
     * @return 格式化地理位置，或「本机」「内网」，无法判断时为空串
     */
    public String resolve(String ip) {
        if (ip == null || ip.isBlank()) {
            return "";
        }
        String value = ip.trim();
        Optional<String> local = classifyLocalOrPrivate(value);
        if (local.isPresent()) {
            return local.get();
        }

        try {
            InetAddress addr = InetAddress.getByName(value);
            Searcher searcher = selectSearcher(addr);
            if (searcher == null) {
                return "";
            }
            // 用规范地址字符串查询，兼容 ::ffff:x.x.x.x 被解析为 IPv4 的情况
            String queryIp = addr.getHostAddress();
            String region = searcher.search(queryIp);
            return formatRegion(region);
        } catch (Exception e) {
            log.atError().addKeyValue("ip", value).setCause(e).log("query login IP location fail");
            return "";
        }
    }

    @PreDestroy
    void close() {
        closeQuietly(v4Searcher, "v4");
        closeQuietly(v6Searcher, "v6");
    }

    private Searcher selectSearcher(InetAddress addr) {
        if (addr instanceof Inet4Address) {
            return v4Searcher;
        }
        if (addr instanceof Inet6Address inet6) {
            // IPv4-mapped / 兼容地址走 v4 库（与 InetAddress 可能直接返回 Inet4Address 的路径一致）
            if (inet6.isIPv4CompatibleAddress() || isIpv4Mapped(inet6)) {
                return v4Searcher != null ? v4Searcher : v6Searcher;
            }
            return v6Searcher;
        }
        return null;
    }

    private static boolean isIpv4Mapped(Inet6Address addr) {
        byte[] bytes = addr.getAddress();
        if (bytes.length != 16) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF;
    }

    private static Searcher loadSearcher(Version version, String classpath) {
        try (InputStream in = IpLocationResolver.class.getClassLoader().getResourceAsStream(classpath)) {
            if (in == null) {
                log.atWarn()
                        .addKeyValue("classpath", classpath)
                        .addKeyValue("version", version.name)
                        .log("ip2region xdb not found on classpath");
                return null;
            }
            LongByteArray content = Searcher.loadContentFromInputStream(in);
            Searcher searcher = Searcher.newWithBuffer(version, content);
            log.atInfo()
                    .addKeyValue("version", version.name)
                    .addKeyValue("classpath", classpath)
                    .addKeyValue("size", content.length())
                    .log("ip2region searcher loaded");
            return searcher;
        } catch (Exception e) {
            log.atError()
                    .addKeyValue("path", classpath)
                    .addKeyValue("version", version.name)
                    .setCause(e)
                    .log("load ip2region xdb failed");
            return null;
        }
    }

    private void closeQuietly(Searcher searcher, String label) {
        if (searcher == null) {
            return;
        }
        try {
            searcher.close();
        } catch (Exception e) {
            log.atWarn().addKeyValue("label", label).setCause(e).log("close ip2region searcher failed");
        }
    }

    /**
     * 将 ip2region 原始 region 格式化为 Go 一致的展示串。
     *
     * <p>xdb 常见：{@code 国家|区域|省|市|ISP} 或 {@code 国家|省|市|ISP}。
     */
    static String formatRegion(String region) {
        if (region == null || region.isBlank() || "0".equals(region)) {
            return "";
        }
        List<String> parts = PIPE.splitToList(region);
        String country;
        String province;
        String city;
        String isp;
        if (parts.size() >= 5) {
            country = clean(parts.get(0));
            province = clean(parts.get(2));
            city = clean(parts.get(3));
            isp = clean(parts.get(4));
        } else if (parts.size() >= 4) {
            country = clean(parts.get(0));
            province = clean(parts.get(1));
            city = clean(parts.get(2));
            isp = clean(parts.get(3));
        } else {
            return region;
        }
        return String.format("国家:%s|省:%s|市:%s|服务:%s", country, province, city, isp);
    }

    private static String clean(String part) {
        if (part == null || part.isBlank() || "0".equals(part)) {
            return "";
        }
        return part.trim();
    }

    static Optional<String> classifyLocalOrPrivate(String ip) {
        if (isLoopbackLiteral(ip)) {
            return Optional.of("本机");
        }
        try {
            InetAddress addr = InetAddress.getByName(ip);
            if (addr.isLoopbackAddress()) {
                return Optional.of("本机");
            }
            if (isPrivateOrLocalAddress(addr)) {
                return Optional.of("内网");
            }
        } catch (Exception ignored) {
            // 回退到简单字符串启发式
            if (isPrivateIpv4(ip) || isPrivateIpv6Literal(ip)) {
                return Optional.of("内网");
            }
        }
        return Optional.empty();
    }

    private static boolean isLoopbackLiteral(String ip) {
        return "127.0.0.1".equals(ip)
                || "::1".equals(ip)
                || "0:0:0:0:0:0:0:1".equals(ip)
                || "0000:0000:0000:0000:0000:0000:0000:0001".equalsIgnoreCase(ip);
    }

    private static boolean isPrivateOrLocalAddress(InetAddress addr) {
        if (addr.isAnyLocalAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()) {
            return true;
        }
        // ULA fc00::/7
        if (addr instanceof Inet6Address) {
            byte first = addr.getAddress()[0];
            return (first & 0xFE) == 0xFC;
        }
        return false;
    }

    private static boolean isPrivateIpv6Literal(String ip) {
        String lower = ip.toLowerCase(Locale.ROOT);
        return lower.startsWith("fc") || lower.startsWith("fd") || lower.startsWith("fe80:");
    }

    private static boolean isPrivateIpv4(String ip) {
        List<String> parts = DOT.splitToList(ip);
        if (parts.size() != 4) {
            return false;
        }
        int firstOctet;
        int secondOctet;
        try {
            firstOctet = Integer.parseInt(parts.get(0));
            secondOctet = Integer.parseInt(parts.get(1));
        } catch (NumberFormatException ex) {
            return false;
        }
        if (firstOctet == 10) {
            return true;
        }
        if (firstOctet == 172 && secondOctet >= 16 && secondOctet <= 31) {
            return true;
        }
        if (firstOctet == 192 && secondOctet == 168) {
            return true;
        }
        return firstOctet == 169 && secondOctet == 254;
    }
}

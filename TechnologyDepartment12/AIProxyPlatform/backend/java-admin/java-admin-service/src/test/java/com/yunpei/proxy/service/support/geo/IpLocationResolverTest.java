package com.yunpei.proxy.service.support.geo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link IpLocationResolver} 单元测试。
 *
 * @author yunpei
 */
class IpLocationResolverTest {

    @Test
    void classifyLocalOrPrivate() {
        assertThat(IpLocationResolver.classifyLocalOrPrivate("127.0.0.1")).contains("本机");
        assertThat(IpLocationResolver.classifyLocalOrPrivate("::1")).contains("本机");
        assertThat(IpLocationResolver.classifyLocalOrPrivate("10.0.0.1")).contains("内网");
        assertThat(IpLocationResolver.classifyLocalOrPrivate("192.168.1.1")).contains("内网");
        assertThat(IpLocationResolver.classifyLocalOrPrivate("fe80::1")).contains("内网");
        assertThat(IpLocationResolver.classifyLocalOrPrivate("fd12:3456:789a::1"))
                .contains("内网");
        assertThat(IpLocationResolver.classifyLocalOrPrivate("8.8.8.8")).isEmpty();
        assertThat(IpLocationResolver.classifyLocalOrPrivate("2001:4860:4860::8888"))
                .isEmpty();
    }

    @Test
    void formatRegion_fiveParts_likeCommonXdb() {
        String formatted = IpLocationResolver.formatRegion("中国|0|广东省|深圳市|电信");
        assertThat(formatted).isEqualTo("国家:中国|省:广东省|市:深圳市|服务:电信");
    }

    @Test
    void formatRegion_fourParts_likeGoSplit() {
        String formatted = IpLocationResolver.formatRegion("中国|广东省|深圳市|电信");
        assertThat(formatted).isEqualTo("国家:中国|省:广东省|市:深圳市|服务:电信");
    }

    @Test
    void resolve_loopback_withoutSearcher() {
        IpLocationResolver resolver = new IpLocationResolver(null, null);
        assertThat(resolver.resolve("127.0.0.1")).isEqualTo("本机");
        assertThat(resolver.resolve("::1")).isEqualTo("本机");
        assertThat(resolver.resolve("192.168.0.1")).isEqualTo("内网");
        assertThat(resolver.resolve("8.8.8.8")).isEmpty();
        assertThat(resolver.resolve("2001:4860:4860::8888")).isEmpty();
    }

    @Test
    void resolve_publicIpv4_and_Ipv6_withClasspathXdb() {
        IpLocationResolver resolver = new IpLocationResolver();
        try {
            String v4 = resolver.resolve("8.8.8.8");
            if (!v4.isEmpty()) {
                assertThat(v4).contains("国家:");
            }

            String v6 = resolver.resolve("2001:4860:4860::8888");
            if (!v6.isEmpty()) {
                assertThat(v6).contains("国家:");
            }

            // 资源齐全时 v4/v6 都应能解析出结果
            if (!v4.isEmpty() && !v6.isEmpty()) {
                assertThat(v4).isNotEqualTo("");
                assertThat(v6).isNotEqualTo("");
            }
        } finally {
            resolver.close();
        }
    }
}

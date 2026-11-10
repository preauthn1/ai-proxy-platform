package com.yunpei.proxy.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link ClientIpUtils} 单元测试：直连、单/多代理、unknown、非法段、IPv6 回环。
 *
 * @author yunpei
 */
class ClientIpUtilsTest {

    @Test
    void resolve_directRemoteAddr() {
        assertThat(ClientIpUtils.resolve(null, null, "123.45.67.89")).isEqualTo("123.45.67.89");
    }

    @Test
    void resolve_singleProxy_xff() {
        assertThat(ClientIpUtils.resolve("123.45.67.89", null, "10.0.0.1")).isEqualTo("123.45.67.89");
    }

    @Test
    void resolve_multiProxy_takesLeftmostValid() {
        assertThat(ClientIpUtils.resolve("123.45.67.89, 10.0.1.100, 10.0.1.101", null, "10.0.0.1"))
                .isEqualTo("123.45.67.89");
        // 与现网 RequestContextFilter 行为一致：内网链也取最左
        assertThat(ClientIpUtils.resolve("10.0.0.8, 10.0.0.1", null, "10.0.0.2"))
                .isEqualTo("10.0.0.8");
    }

    @Test
    void resolve_skipsUnknownAndInvalidTokens() {
        assertThat(ClientIpUtils.resolve("unknown, 203.0.113.9", null, "10.0.0.1"))
                .isEqualTo("203.0.113.9");
        assertThat(ClientIpUtils.resolve("not-an-ip, 198.51.100.7", null, "10.0.0.1"))
                .isEqualTo("198.51.100.7");
        assertThat(ClientIpUtils.resolve("  , unknown ,  ", "203.0.113.1", "10.0.0.1"))
                .isEqualTo("203.0.113.1");
    }

    @Test
    void resolve_xRealIpAndOtherHeaders() {
        assertThat(ClientIpUtils.resolve(null, "203.0.113.50", "10.0.0.1")).isEqualTo("203.0.113.50");
        assertThat(ClientIpUtils.resolve(null, null, "10.0.0.1", "Proxy-Client-IP-invalid", "198.51.100.20"))
                .isEqualTo("198.51.100.20");
        assertThat(ClientIpUtils.resolve(null, "unknown", "127.0.0.1", "198.51.100.30"))
                .isEqualTo("198.51.100.30");
    }

    @Test
    void resolve_ipv6Loopback_normalizedToIpv4Localhost() {
        assertThat(ClientIpUtils.resolve(null, null, "::1")).isEqualTo("127.0.0.1");
        assertThat(ClientIpUtils.resolve(null, null, "0:0:0:0:0:0:0:1")).isEqualTo("127.0.0.1");
        assertThat(ClientIpUtils.resolve("::1", null, "10.0.0.1")).isEqualTo("127.0.0.1");
    }

    @Test
    void resolve_ipv6Public() {
        assertThat(ClientIpUtils.resolve("2001:db8::1", null, "10.0.0.1")).isEqualTo("2001:db8::1");
    }

    @Test
    void resolve_allMissing_returnsEmpty() {
        assertThat(ClientIpUtils.resolve(null, null, null)).isEmpty();
        assertThat(ClientIpUtils.resolve("  ", "unknown", null)).isEmpty();
    }

    @Test
    void isValidIpAddress_rejectsUnknownAndHostnames() {
        assertThat(ClientIpUtils.isValidIpAddress("unknown")).isFalse();
        assertThat(ClientIpUtils.isValidIpAddress("")).isFalse();
        assertThat(ClientIpUtils.isValidIpAddress("example.com")).isFalse();
        assertThat(ClientIpUtils.isValidIpAddress("256.1.1.1")).isFalse();
        assertThat(ClientIpUtils.isValidIpAddress("1.2.3.4")).isTrue();
        assertThat(ClientIpUtils.isValidIpAddress("::ffff:192.0.2.1")).isTrue();
    }
}

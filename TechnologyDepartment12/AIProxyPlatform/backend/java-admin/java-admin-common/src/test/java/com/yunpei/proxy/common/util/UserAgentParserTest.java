package com.yunpei.proxy.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link UserAgentParser} 单元测试。
 *
 * @author yunpei
 */
class UserAgentParserTest {

    @Test
    void parse_nullOrBlank_returnsEmpty() {
        assertThat(UserAgentParser.parse(null).browserName()).isEmpty();
        assertThat(UserAgentParser.parse("  ").osName()).isEmpty();
        assertThat(UserAgentParser.parse(null).clientName()).isEmpty();
    }

    @Test
    void parse_chromeOnWindows_isDesktopPc() {
        String ua =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
        UserAgentParser.Parsed parsed = UserAgentParser.parse(ua);
        assertThat(parsed.browserName()).isEqualTo("Chrome");
        assertThat(parsed.browserVersion()).startsWith("122.");
        assertThat(parsed.osName()).isEqualTo("Windows");
        assertThat(parsed.osVersion()).isEqualTo("10/11");
        assertThat(parsed.desktop()).isTrue();
        assertThat(parsed.clientName()).isEqualTo("PC");
    }

    @Test
    void parse_edgePreferredOverChromeToken() {
        String ua =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36 Edg/122.0.0.0";
        UserAgentParser.Parsed parsed = UserAgentParser.parse(ua);
        assertThat(parsed.browserName()).isEqualTo("Edge");
        assertThat(parsed.browserVersion()).startsWith("122.");
    }

    @Test
    void parse_safariOnMac() {
        String ua =
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15";
        UserAgentParser.Parsed parsed = UserAgentParser.parse(ua);
        assertThat(parsed.browserName()).isEqualTo("Safari");
        assertThat(parsed.browserVersion()).isEqualTo("17.2");
        assertThat(parsed.osName()).isEqualTo("macOS");
        assertThat(parsed.osVersion()).isEqualTo("10.15.7");
        assertThat(parsed.clientName()).isEqualTo("PC");
    }

    @Test
    void parse_iphone_deviceAsClientName() {
        String ua =
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1";
        UserAgentParser.Parsed parsed = UserAgentParser.parse(ua);
        assertThat(parsed.osName()).isEqualTo("iOS");
        assertThat(parsed.device()).isEqualTo("iPhone");
        assertThat(parsed.desktop()).isFalse();
        assertThat(parsed.clientName()).isEqualTo("iPhone");
    }

    @Test
    void parse_unknownBrowser_marksUnknown() {
        UserAgentParser.Parsed parsed = UserAgentParser.parse("CustomBot/1.0");
        assertThat(parsed.browserName()).isEqualTo("Unknown");
        assertThat(parsed.browserVersion()).isEmpty();
        assertThat(parsed.clientName()).isEmpty();
    }
}

package com.yunpei.proxy.infra.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunpei.proxy.common.request.RequestContext;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.service.blacklist.BlacklistService;
import com.yunpei.proxy.service.blacklist.BlacklistService.BlacklistHit;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * S2：黑名单 Filter 外部行为 — Access Blocked、固定文案、不回传 reason；LOGIN IP / API IP+USER。
 */
class BlacklistFilterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BlacklistService blacklistService;
    private BlacklistFilter filter;

    @BeforeEach
    void setUp() {
        blacklistService = mock(BlacklistService.class);
        filter = new BlacklistFilter(blacklistService);
        RequestContext.open();
    }

    @AfterEach
    void tearDown() {
        RequestContext.close();
    }

    @Test
    void login_ipHit_returnsAccessBlockedWithoutReason() throws Exception {
        when(blacklistService.findBlockingHit(eq("IP"), eq("10.0.0.9"), eq("LOGIN"), isNull()))
                .thenReturn(Optional.of(new BlacklistHit("IP", "10.0.0.9", "ALL", "do-not-leak")));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        RequestContext.setClientIp("10.0.0.9");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(req, resp, (r, s) -> chainCalled.set(true));

        assertThat(chainCalled).isFalse();
        assertThat(resp.getStatus()).isEqualTo(403);
        JsonNode body = MAPPER.readTree(resp.getContentAsString());
        assertThat(body.get("code").asInt()).isEqualTo(ResultCode.ACCESS_BLOCKED.getCode());
        assertThat(body.get("msg").asText()).isEqualTo(ResultCode.ACCESS_BLOCKED.getMsg());
        assertThat(resp.getContentAsString()).doesNotContain("do-not-leak");
        verify(blacklistService, never()).findBlockingHit(eq("USER"), any(), any(), any());
    }

    @Test
    void login_trailingSlash_stillUsesLoginScopeForIp() throws Exception {
        when(blacklistService.findBlockingHit(eq("IP"), eq("10.0.0.9"), eq("LOGIN"), isNull()))
                .thenReturn(Optional.of(new BlacklistHit("IP", "10.0.0.9", "LOGIN", "r")));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login/");
        RequestContext.setClientIp("10.0.0.9");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(req, resp, (r, s) -> chainCalled.set(true));

        assertThat(chainCalled).isFalse();
        verify(blacklistService).findBlockingHit(eq("IP"), eq("10.0.0.9"), eq("LOGIN"), isNull());
        verify(blacklistService, never()).findBlockingHit(eq("IP"), eq("10.0.0.9"), eq("API"), isNull());
    }

    @Test
    void login_ipMiss_passesAndDoesNotCheckUserInFilter() throws Exception {
        when(blacklistService.findBlockingHit(eq("IP"), eq("127.0.0.1"), eq("LOGIN"), isNull()))
                .thenReturn(Optional.empty());

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        RequestContext.setClientIp("127.0.0.1");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(req, resp, (r, s) -> chainCalled.set(true));

        assertThat(chainCalled).isTrue();
        verify(blacklistService, never()).findBlockingHit(eq("USER"), any(), any(), any());
    }

    @Test
    void api_ipHit_returnsAccessBlocked() throws Exception {
        when(blacklistService.findBlockingHit(eq("IP"), eq("1.1.1.1"), eq("API"), isNull()))
                .thenReturn(Optional.of(new BlacklistHit("IP", "1.1.1.1", "API", "scan")));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/system/user/list");
        RequestContext.setClientIp("1.1.1.1");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(req, resp, (r, s) -> chainCalled.set(true));

        assertThat(chainCalled).isFalse();
        JsonNode body = MAPPER.readTree(resp.getContentAsString());
        assertThat(body.get("code").asInt()).isEqualTo(ResultCode.ACCESS_BLOCKED.getCode());
        assertThat(body.get("msg").asText()).isEqualTo("Access Blocked");
        assertThat(resp.getContentAsString()).doesNotContain("scan");
    }

    @Test
    void api_loggedInUserHit_returnsAccessBlocked() throws Exception {
        when(blacklistService.findBlockingHit(eq("IP"), eq("2.2.2.2"), eq("API"), isNull()))
                .thenReturn(Optional.empty());
        when(blacklistService.findBlockingHit(eq("USER"), eq("99"), eq("API"), isNull()))
                .thenReturn(Optional.of(new BlacklistHit("USER", "99", "ALL", "internal-only")));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/system/menu/list");
        RequestContext.setClientIp("2.2.2.2");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(99L);

            filter.doFilter(req, resp, (r, s) -> chainCalled.set(true));
        }

        assertThat(chainCalled).isFalse();
        assertThat(resp.getStatus()).isEqualTo(403);
        JsonNode body = MAPPER.readTree(resp.getContentAsString());
        assertThat(body.get("code").asInt()).isEqualTo(ResultCode.ACCESS_BLOCKED.getCode());
        assertThat(body.get("msg").asText()).isEqualTo(ResultCode.ACCESS_BLOCKED.getMsg());
        assertThat(resp.getContentAsString()).doesNotContain("internal-only");
    }

    @Test
    void api_userHit_viaRequestContextUserId() throws Exception {
        when(blacklistService.findBlockingHit(eq("IP"), eq("4.4.4.4"), eq("API"), isNull()))
                .thenReturn(Optional.empty());
        when(blacklistService.findBlockingHit(eq("USER"), eq("7"), eq("API"), isNull()))
                .thenReturn(Optional.of(new BlacklistHit("USER", "7", "API", "ban")));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/system/user/list");
        RequestContext.setClientIp("4.4.4.4");
        RequestContext.setUserId(7L);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(req, resp, (r, s) -> chainCalled.set(true));

        assertThat(chainCalled).isFalse();
        assertThat(resp.getStatus()).isEqualTo(403);
        JsonNode body = MAPPER.readTree(resp.getContentAsString());
        assertThat(body.get("code").asInt()).isEqualTo(ResultCode.ACCESS_BLOCKED.getCode());
    }

    @Test
    void api_notLoggedIn_onlyChecksIp() throws Exception {
        when(blacklistService.findBlockingHit(eq("IP"), eq("3.3.3.3"), eq("API"), isNull()))
                .thenReturn(Optional.empty());

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/auth/info");
        RequestContext.setClientIp("3.3.3.3");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);

            filter.doFilter(req, resp, (r, s) -> chainCalled.set(true));
        }

        assertThat(chainCalled).isTrue();
        verify(blacklistService, never()).findBlockingHit(eq("USER"), any(), any(), any());
    }

    @Test
    void nonApiPath_skipsBlacklist() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/doc.html");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(req, resp, (r, s) -> chainCalled.set(true));

        assertThat(chainCalled).isTrue();
        verify(blacklistService, never()).findBlockingHit(any(), any(), any(), any());
    }
}

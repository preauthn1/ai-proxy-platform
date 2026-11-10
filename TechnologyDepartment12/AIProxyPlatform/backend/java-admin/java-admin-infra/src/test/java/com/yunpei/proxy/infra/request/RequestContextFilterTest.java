package com.yunpei.proxy.infra.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunpei.proxy.common.constant.SecurityHeaders;
import com.yunpei.proxy.common.request.RequestContext;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * RequestContextFilter：open/fill/close 生命周期。
 *
 * @author yunpei
 */
class RequestContextFilterTest {

    private final RequestContextFilter filter = new RequestContextFilter();

    @AfterEach
    void clearRequestContext() {
        RequestContext.close();
    }

    @Test
    void filter_fillsRequestIdUriAndClearsAfter() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/user/info");
        req.addHeader(SecurityHeaders.REQUEST_ID, "nid-9");
        req.addHeader("X-Forwarded-For", "10.0.0.8, 10.0.0.1");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        AtomicReference<String> seenId = new AtomicReference<>();
        AtomicReference<String> seenUri = new AtomicReference<>();
        AtomicReference<String> seenIp = new AtomicReference<>();

        filter.doFilter(req, resp, (r, s) -> {
            seenId.set(RequestContext.requestIdOrNull());
            seenUri.set(RequestContext.requestUriOrNull());
            seenIp.set(RequestContext.clientIpOrNull());
        });

        assertThat(seenId.get()).isEqualTo("nid-9");
        assertThat(seenUri.get()).isEqualTo("/api/user/info");
        assertThat(seenIp.get()).isEqualTo("10.0.0.8");
        // finally 已清理
        assertThat(RequestContext.get()).isNull();
    }
}

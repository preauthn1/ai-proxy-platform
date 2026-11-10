package com.yunpei.proxy.common.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * RequestContext ThreadLocal 行为。
 *
 * @author yunpei
 */
class RequestContextTest {

    @AfterEach
    void clearRequestContext() {
        RequestContext.close();
    }

    @Test
    void open_set_and_read_fields() {
        RequestContext.open();
        RequestContext.setUserId(42L);
        RequestContext.setLanguage("zh-CN");
        RequestContext.setRequestId("req-1");
        RequestContext.setRequestUri("/api/user/info");
        RequestContext.setClientIp("1.2.3.4");

        assertThat(RequestContext.userIdOrNull()).isEqualTo(42L);
        assertThat(RequestContext.languageOrNull()).isEqualTo("zh-CN");
        assertThat(RequestContext.requestIdOrNull()).isEqualTo("req-1");
        assertThat(RequestContext.requestUriOrNull()).isEqualTo("/api/user/info");
        assertThat(RequestContext.clientIpOrNull()).isEqualTo("1.2.3.4");
        assertThat(RequestContext.get()).isNotNull();
        assertThat(RequestContext.get().getUserId()).isEqualTo(42L);
    }

    @Test
    void close_clearsThreadLocal() {
        RequestContext.open();
        RequestContext.setUserId(1L);
        RequestContext.close();

        assertThat(RequestContext.get()).isNull();
        assertThat(RequestContext.userIdOrNull()).isNull();
        assertThat(RequestContext.languageOrNull()).isNull();
    }

    @Test
    void withoutOpen_gettersReturnNull() {
        assertThat(RequestContext.get()).isNull();
        assertThat(RequestContext.userIdOrNull()).isNull();
    }
}

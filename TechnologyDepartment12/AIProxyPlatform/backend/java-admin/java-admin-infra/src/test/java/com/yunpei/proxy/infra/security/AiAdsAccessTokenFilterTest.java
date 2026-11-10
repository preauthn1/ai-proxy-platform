package com.yunpei.proxy.infra.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link AiAdsAccessTokenFilter} 契约测试：有/无/错 token → 通过/401；配置为空放行；非拦截路径放行。
 */
class AiAdsAccessTokenFilterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String INTERCEPT_PATH = "/api/v1/integrations/image-generation/images";

    private AiAdsAccessTokenProperties properties;
    private AiAdsAccessTokenFilter filter;

    @BeforeEach
    void init() {
        properties = new AiAdsAccessTokenProperties();
        filter = new AiAdsAccessTokenFilter(properties);
    }

    private static MockHttpServletRequest request(String path) {
        return new MockHttpServletRequest("GET", path);
    }

    @Test
    void validBearerToken_passes() throws Exception {
        properties.setAccessToken("secret");
        MockHttpServletRequest req = request(INTERCEPT_PATH);
        req.addHeader("Authorization", "Bearer secret");
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(req, new MockHttpServletResponse(), (r, s) -> called.set(true));

        assertThat(called).isTrue();
    }

    @Test
    void missingToken_returns401() throws Exception {
        properties.setAccessToken("secret");
        MockHttpServletRequest req = request(INTERCEPT_PATH);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(req, resp, (r, s) -> called.set(true));

        assertThat(called).isFalse();
        assertThat(resp.getStatus()).isEqualTo(401);
        JsonNode body = MAPPER.readTree(resp.getContentAsString());
        assertThat(body.get("code").asInt()).isEqualTo(4001);
        assertThat(body.get("message").asText()).isEqualTo("unauthorized");
    }

    @Test
    void wrongToken_returns401() throws Exception {
        properties.setAccessToken("secret");
        MockHttpServletRequest req = request(INTERCEPT_PATH);
        req.addHeader("Authorization", "Bearer wrong");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(req, resp, (r, s) -> called.set(true));

        assertThat(called).isFalse();
        assertThat(resp.getStatus()).isEqualTo(401);
    }

    @Test
    void emptyConfig_passes() throws Exception {
        // accessToken 未配置 → 放行（对齐 Python 语义）
        MockHttpServletRequest req = request(INTERCEPT_PATH);
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(req, new MockHttpServletResponse(), (r, s) -> called.set(true));

        assertThat(called).isTrue();
    }

    @Test
    void queryAccessToken_passes() throws Exception {
        properties.setAccessToken("secret");
        MockHttpServletRequest req = request("/api/v1/integrations/image-generation/jobs/abc");
        req.addParameter("access_token", "secret");
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(req, new MockHttpServletResponse(), (r, s) -> called.set(true));

        assertThat(called).isTrue();
    }

    @Test
    void nonInterceptedPath_passesWithoutToken() throws Exception {
        properties.setAccessToken("secret");
        MockHttpServletRequest req = request("/api/auth/login");
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(req, new MockHttpServletResponse(), (r, s) -> called.set(true));

        assertThat(called).isTrue();
    }

    @Test
    void videoPath_requiresToken() throws Exception {
        properties.setAccessToken("secret");
        MockHttpServletRequest req = request("/api/v1/integrations/video-generation/videos");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(req, resp, (r, s) -> called.set(true));

        assertThat(called).isFalse();
        assertThat(resp.getStatus()).isEqualTo(401);
    }

    @Test
    void videoPath_validBearer_passes() throws Exception {
        properties.setAccessToken("secret");
        MockHttpServletRequest req = request("/api/v1/integrations/video-generation/jobs/abc");
        req.addHeader("Authorization", "Bearer secret");
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(req, new MockHttpServletResponse(), (r, s) -> called.set(true));

        assertThat(called).isTrue();
    }

    @Test
    void aiPath_requiresToken() throws Exception {
        properties.setAccessToken("secret");
        MockHttpServletRequest req = request("/api/v1/integrations/ai/copy");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(req, resp, (r, s) -> called.set(true));

        assertThat(called).isFalse();
        assertThat(resp.getStatus()).isEqualTo(401);
        JsonNode body = MAPPER.readTree(resp.getContentAsString());
        assertThat(body.get("code").asInt()).isEqualTo(4001);
        assertThat(body.get("message").asText()).isEqualTo("unauthorized");
    }

    @Test
    void aiPath_validBearer_passes() throws Exception {
        properties.setAccessToken("secret");
        MockHttpServletRequest req = request("/api/v1/integrations/ai/jobs/abc");
        req.addHeader("Authorization", "Bearer secret");
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(req, new MockHttpServletResponse(), (r, s) -> called.set(true));

        assertThat(called).isTrue();
    }

    /** 联调硬化回归：AI 路径接入后 image-generation 鉴权路径不变。 */
    @Test
    void imagePath_stillRequiresToken_afterAiIntegration() throws Exception {
        properties.setAccessToken("secret");
        MockHttpServletRequest req = request("/api/v1/integrations/image-generation/jobs/x");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(req, resp, (r, s) -> called.set(true));

        assertThat(called).isFalse();
        assertThat(resp.getStatus()).isEqualTo(401);
    }

    /** 联调硬化回归：video-generation 路径仍被同一 Filter 拦截。 */
    @Test
    void videoPath_stillRequiresToken_afterAiIntegration() throws Exception {
        properties.setAccessToken("secret");
        MockHttpServletRequest req = request("/api/v1/integrations/video-generation/jobs/x");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(req, resp, (r, s) -> called.set(true));

        assertThat(called).isFalse();
        assertThat(resp.getStatus()).isEqualTo(401);
    }

    @Test
    void adPerformancePath_requiresToken() throws Exception {
        properties.setAccessToken("secret");
        MockHttpServletRequest req = request("/api/v1/integrations/ad-performance/analysis-jobs");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(req, resp, (r, s) -> called.set(true));

        assertThat(called).isFalse();
        assertThat(resp.getStatus()).isEqualTo(401);
    }

    @Test
    void adPerformancePath_validBearer_passes() throws Exception {
        properties.setAccessToken("secret");
        MockHttpServletRequest req = request("/api/v1/integrations/ad-performance/analysis-jobs/ana_1");
        req.addHeader("Authorization", "Bearer secret");
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(req, new MockHttpServletResponse(), (r, s) -> called.set(true));

        assertThat(called).isTrue();
    }
}

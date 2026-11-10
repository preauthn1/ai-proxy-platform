package com.yunpei.proxy.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 外部集成共享密钥 Filter（T1 鉴权）。
 *
 * <p>拦截 {@code /api/v1/integrations/image-generation/**}、
 * {@code /api/v1/integrations/video-generation/**} 与
 * {@code /api/v1/integrations/ai/**}、
 * {@code /api/v1/integrations/ad-performance/**}：外部调用方不带 admin 登录态 / 内部签名头，
 * 该路径已从 Sa-Token / Casbin / Encrypt / Sign 排除，仅由此 Filter 用共享密钥鉴权。
 *
 * <p>接受 {@code Authorization: Bearer <token>} 或 query {@code access_token} / {@code ai_access_token}，
 * 与配置值 {@code app.ai-ads.access-token} 常量时间比较；配置为空 → 放行（对齐 Python 语义）。
 * 失败 → HTTP 401 + 外部信封 {@code {code:4001, message:"unauthorized", data:{}}}。
 *
 * <p>Order 置于安全中间件之后（+8）、业务逻辑之前。
 *
 * @author yunpei
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 8)
public final class AiAdsAccessTokenFilter extends OncePerRequestFilter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final String[] INTERCEPT_PATTERNS = {
        "/api/v1/integrations/image-generation/**",
        "/api/v1/integrations/video-generation/**",
        "/api/v1/integrations/ai/**",
        "/api/v1/integrations/ad-performance/**"
    };
    private static final String BEARER_PREFIX = "Bearer ";

    private final AiAdsAccessTokenProperties properties;

    public AiAdsAccessTokenFilter(AiAdsAccessTokenProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = SecurityPathMatcher.normalizePath(request);
        for (String pattern : INTERCEPT_PATTERNS) {
            if (PATH_MATCHER.match(pattern, path)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String configured = properties.getAccessToken();
        if (configured == null || configured.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        String provided = resolveToken(request);
        if (provided != null && constantTimeEquals(provided, configured)) {
            filterChain.doFilter(request, response);
            return;
        }
        log.debug("ai-ads access token 缺失或错误，拒绝 path={}", request.getRequestURI());
        writeUnauthorized(response);
    }

    private static String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String token = authorization.substring(BEARER_PREFIX.length()).trim();
            if (!token.isEmpty()) {
                return token;
            }
        }
        String REDACTED("access_token");
        if (accessToken != null && !accessToken.isEmpty()) {
            return accessToken;
        }
        String aiREDACTED("ai_access_token");
        if (aiAccessToken != null && !aiAccessToken.isEmpty()) {
            return aiAccessToken;
        }
        return null;
    }

    /** 常量时间比较，避免时序侧信道。 */
    private static boolean constantTimeEquals(String expected, String provided) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter()
                .write(OBJECT_MAPPER.writeValueAsString(
                        Map.of("code", 4001, "message", "unauthorized", "data", Map.of())));
    }
}

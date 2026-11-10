package com.yunpei.proxy.infra.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.util.AntPathMatcher;

/**
 * 请求安全路径匹配（Encrypt 白名单等）。
 *
 * @author yunpei
 */
public final class SecurityPathMatcher {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * 免强制加密与独立 Sign 校验的路径（公钥、ALTCHA、公开 i18n、文档、健康检查等）。
     *
     * <p>含 {@code /webjars/**}、{@code /swagger-resources/**}、{@code /csrf}，
     * 否则打开 {@code /doc.html} 时其加载的静态资源会被 Sign/Encrypt 拦截。
     * 同时作为 {@link com.yunpei.proxy.infra.security.SecurityProperties} 白名单默认值。
     */
    public static final List<String> SECURITY_WHITELIST = List.of(
            "/api/encrypt/public/key",
            // 仅 dev profile 注册 DevEncryptController；prod 无此路径
            "/api/encrypt/dev/key-pair",
            "/api/encrypt/dev/session-key",
            "/api/altcha/**",
            // 进页/未登录拉取翻译包
            "/api/public/i18n/**",
            "/doc.html",
            "/doc.html/**",
            "/swagger-ui/**",
            "/swagger-resources/**",
            "/v3/api-docs/**",
            "/webjars/**",
            "/favicon.ico",
            "/csrf",
            "/error",
            "/actuator/**",
            "/api/health/**",
            // 外部集成路径：跳过 Encrypt/Sign（仅共享密钥 Filter 鉴权）
            "/api/v1/integrations/**");

    private SecurityPathMatcher() {}

    public static boolean isWhitelisted(HttpServletRequest request) {
        return isWhitelisted(request, SECURITY_WHITELIST);
    }

    public static boolean isWhitelisted(HttpServletRequest request, List<String> patterns) {
        String path = normalizePath(request);
        for (String pattern : patterns) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    public static String normalizePath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (path.isEmpty()) {
            return "/";
        }
        return path;
    }
}

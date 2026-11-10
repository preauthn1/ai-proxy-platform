package com.yunpei.proxy.infra.security;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 请求安全中间件开关（Timestamp / Encrypt / Nonce / Sign / Language 独立）。
 *
 * <p>对应 {@code app.security.*}；dev 默认全开。
 *
 * <p>路径类配置（{@code whitelist} / {@code auth-exclude-paths} /
 * {@code casbin-exclude-paths}）在 {@code application.yaml} 中整体覆盖；未配置时回退到本类默认值。
 *
 * @author yunpei
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    private Timestamp timestamp = new Timestamp();
    private Encrypt encrypt = new Encrypt();
    private Nonce nonce = new Nonce();
    private Sign sign = new Sign();
    private Language language = new Language();

    /** Encrypt / Sign 安全中间件白名单（免强制加密与独立签名校验的路径）。 */
    private List<String> whitelist = defaultWhitelist();

    /** Sa-Token 认证拦截器（StpUtil.checkLogin）排除路径。 */
    private List<String> authExcludePaths = defaultAuthExcludePaths();

    /** jcasbin 授权拦截器（deny-by-default）排除路径。 */
    private List<String> casbinExcludePaths = defaultCasbinExcludePaths();

    /**
     * 安全中间件白名单默认值：复用 {@link SecurityPathMatcher#SECURITY_WHITELIST}
     * （含 Knife4j/springdoc 静态资源，避免打开 {@code /doc.html} 时静态资源被拦截）。
     */
    private static List<String> defaultWhitelist() {
        return new ArrayList<>(SecurityPathMatcher.SECURITY_WHITELIST);
    }

    /** Sa-Token 认证拦截器默认排除路径（登录/登出等无需 token 的公开路径）。 */
    private static List<String> defaultAuthExcludePaths() {
        return new ArrayList<>(List.of(
                "/api/auth/login",
                // 登出幂等：未登录也返回成功，不强制 token
                "/api/auth/logout",
                "/api/altcha/challenge",
                "/api/encrypt/public/key",
                // 进页/未登录拉取后端翻译
                "/api/public/i18n/**",
                // dev-only：mock 拉密钥对；prod 无此 Controller
                "/api/encrypt/dev/key-pair"));
    }

    /** jcasbin 授权拦截器默认排除路径（登录/登出 + 文档/静态资源）。 */
    private static List<String> defaultCasbinExcludePaths() {
        List<String> paths = new ArrayList<>(defaultAuthExcludePaths());
        paths.addAll(List.of(
                "/doc.html",
                "/doc.html/**",
                "/swagger-ui/**",
                "/swagger-resources/**",
                "/v3/api-docs/**",
                "/webjars/**",
                "/favicon.ico",
                "/csrf",
                "/error"));
        return paths;
    }

    @Data
    public static class Timestamp {
        /** 是否校验请求时间窗。 */
        private boolean enabled = true;
        /** 允许的时间偏差（毫秒），默认 5 分钟。 */
        private long expireMs = 5 * 60 * 1000L;
    }

    @Data
    public static class Encrypt {
        /** 是否强制请求加密（白名单除外）并加解密 body。 */
        private boolean enabled = true;
    }

    @Data
    public static class Nonce {
        /** 是否拒绝重复 X-Request-ID。 */
        private boolean enabled = true;
        /**
         * Nonce TTL（毫秒）。小于等于 0 时使用 timestamp.expireMs * 2（对齐 Go）。
         */
        private long expireMs = 0;
    }

    @Data
    public static class Sign {
        /** 是否在 Encrypt 关闭时独立校验签名。 */
        private boolean enabled = true;
    }

    @Data
    public static class Language {
        /** 是否解析 X-Language / Accept-Language 并写入请求上下文。 */
        private boolean enabled = true;
    }

    /** Nonce 有效 TTL：显式配置优先，否则 2 倍时间窗。 */
    public long resolveNonceExpireMs() {
        long configured = nonce != null ? nonce.getExpireMs() : 0;
        if (configured > 0) {
            return configured;
        }
        long window = timestamp != null ? timestamp.getExpireMs() : 5 * 60 * 1000L;
        return window * 2;
    }
}

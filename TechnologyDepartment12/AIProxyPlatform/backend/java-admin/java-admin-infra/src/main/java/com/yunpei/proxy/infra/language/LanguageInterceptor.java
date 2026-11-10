package com.yunpei.proxy.infra.language;

import com.yunpei.proxy.common.constant.SecurityHeaders;
import com.yunpei.proxy.common.request.RequestContext;
import com.yunpei.proxy.infra.satoken.SaTokenConfigure;
import com.yunpei.proxy.infra.security.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 填充请求上下文中的用户与语言。
 *
 * <p>须注册在 {@code SaInterceptor} 之后：
 * <ul>
 *   <li>始终写入 {@link RequestContext#setUserId}（若已登录）</li>
 *   <li>Language 开：解析 X-Language / Accept-Language → {@link RequestContext}</li>
 *   <li>已登录且语言与用户 languageCode 不同时异步落库</li>
 * </ul>
 *
 * @author yunpei
 */
@Slf4j
@Component
public final class LanguageInterceptor implements HandlerInterceptor {

    private final SecurityProperties securityProperties;
    private final UserLanguageSyncService userLanguageSyncService;

    public LanguageInterceptor(SecurityProperties securityProperties, UserLanguageSyncService userLanguageSyncService) {
        this.securityProperties = securityProperties;
        this.userLanguageSyncService = userLanguageSyncService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Long userId = SaTokenConfigure.currentUserIdOrNull();
        if (userId != null) {
            RequestContext.setUserId(userId);
        }

        if (!securityProperties.getLanguage().isEnabled()) {
            return true;
        }

        String language = resolveLanguage(request);
        if (language != null && !language.isEmpty()) {
            RequestContext.setLanguage(language);
            log.atDebug().addKeyValue("language", language).log("Request language");

            userLanguageSyncService.syncIfChanged(userId, language);
        }
        return true;
    }

    /** 优先 X-Language，回退 Accept-Language（取第一个 language-tag，去掉 q 值）。 */
    static String resolveLanguage(HttpServletRequest request) {
        String xLanguage = request.getHeader(SecurityHeaders.LANGUAGE);
        if (xLanguage != null) {
            String trimmed = xLanguage.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return parseAcceptLanguage(request.getHeader("Accept-Language"));
    }

    static String parseAcceptLanguage(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return null;
        }
        int comma = acceptLanguage.indexOf(',');
        String first = (comma >= 0 ? acceptLanguage.substring(0, comma) : acceptLanguage).trim();
        int semi = first.indexOf(';');
        if (semi >= 0) {
            first = first.substring(0, semi).trim();
        }
        return first.isEmpty() ? null : first;
    }
}

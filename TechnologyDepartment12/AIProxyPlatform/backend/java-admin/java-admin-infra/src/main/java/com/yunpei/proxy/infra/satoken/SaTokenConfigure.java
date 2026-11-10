package com.yunpei.proxy.infra.satoken;

import cn.dev33.satoken.stp.StpUtil;
import com.yunpei.proxy.common.request.RequestContext;
import org.springframework.context.annotation.Configuration;

/**
 * Sa-Token 工具。
 *
 * <p>当前 Sa-Token 最新版（1.45.0）的 {@code SaServletFilter} 仍基于
 * {@code javax.servlet.Filter}，与 Spring Boot 4 (Jakarta EE 11) 不兼容。
 * <p>本框架<strong>不</strong>用 {@code SaServletFilter}，仅用 {@code SaInterceptor}
 * （在 {@code WebConfig} 注册）；本类仅保留当前登录用户的工具方法。
 * <p>使用 {@code proxyBeanMethods = false}，避免 CGLIB 增强失败（见 {@link com.yunpei.proxy.infra.satoken.SaTokenConfig}）。
 *
 * <p>业务侧优先使用 {@link RequestContext#userIdOrNull()}；本方法在上下文未填充时回退读 Sa-Token。
 *
 * @author yunpei
 */
@Configuration(proxyBeanMethods = false)
@SuppressWarnings("checkstyle:HideUtilityClassConstructor") // Spring 需要可实例化的 lite @Configuration bean
public class SaTokenConfigure {

    /**
     * 当前登录用户 id；未登录返回 {@code null}。
     *
     * <p>优先 {@link RequestContext}（Language 拦截器已写入），否则读 Sa-Token。
     */
    public static Long currentUserIdOrNull() {
        Long fromCtx = RequestContext.userIdOrNull();
        if (fromCtx != null) {
            return fromCtx;
        }
        try {
            return StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        } catch (Exception e) {
            return null;
        }
    }
}

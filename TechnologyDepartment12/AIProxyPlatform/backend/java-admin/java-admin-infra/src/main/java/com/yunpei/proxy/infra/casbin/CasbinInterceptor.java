package com.yunpei.proxy.infra.casbin;

import com.yunpei.proxy.common.exception.AuthException;
import com.yunpei.proxy.infra.satoken.SaTokenConfigure;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.casbin.jcasbin.main.Enforcer;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * jcasbin 全局鉴权拦截器。
 *
 * <p>注册在 {@link com.yunpei.proxy.infra.web.WebConfig} 中，位于 Sa-Token {@code SaInterceptor} 之后。
 * 每次请求从 Sa-Token 取当前用户 ID，调 {@code enforcer.enforce(userId, path, method)} 判断是否放行。
 *
 * <p>标准 casbin 语义：无匹配 policy 时拒绝（deny-by-default）。
 * dev seed（Flyway V2）为 Root（userId=1）写入 {@code p, 1, /*, *} 通配策略；
 * 其他用户须通过角色 API 绑定展开后的 p 策略访问。
 *
 * <p>排除路径：登录接口由 WebConfig 配置排除，不经过本拦截器。
 *
 * @author yunpei
 */
@Slf4j
@RequiredArgsConstructor
public final class CasbinInterceptor implements HandlerInterceptor {

    private final Enforcer enforcer;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Sa-Token 已在 SaInterceptor 中完成登录校验；此处取 userId
        Long userId = SaTokenConfigure.currentUserIdOrNull();
        if (userId == null) {
            // 未登录请求不应到达这里（SaInterceptor 已拦截），防御性拒绝
            throw AuthException.notLogin();
        }

        String sub = String.valueOf(userId);
        String obj = request.getRequestURI();
        String act = request.getMethod();

        boolean allowed = enforcer.enforce(sub, obj, act);
        if (!allowed) {
            log.atWarn()
                    .addKeyValue("sub", sub)
                    .addKeyValue("obj", obj)
                    .addKeyValue("act", act)
                    .addKeyValue("logType", "CASBIN")
                    .log("denied");
            throw AuthException.forbidden();
        }

        return true;
    }
}

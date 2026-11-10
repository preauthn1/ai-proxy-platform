package com.yunpei.proxy.infra.web;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.yunpei.proxy.infra.casbin.CasbinInterceptor;
import com.yunpei.proxy.infra.language.LanguageInterceptor;
import com.yunpei.proxy.infra.security.SecurityProperties;
import org.casbin.jcasbin.main.Enforcer;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册 Sa-Token 拦截器 + Language + jcasbin 鉴权拦截器 + CORS。
 *
 * <p>拦截器顺序：SaInterceptor（认证）→ LanguageInterceptor（语言上下文/异步偏好）→
 * CasbinInterceptor（授权）。
 *
 * <p><strong>登录校验的唯一入口</strong>是本类注册的 {@code SaInterceptor}（{@code StpUtil.checkLogin()}）。
 * Controller 不再重复 {@code requireLogin}/{@code isLogin} 门闩；业务侧只在需要时读取 loginId。
 * CasbinInterceptor 仅做授权（deny-by-default），排除登录/登出等公开路径与文档路径。
 *
 * <p>认证/授权拦截器排除路径在 {@code application.yaml} 的 {@code app.security.auth-exclude-paths} /
 * {@code app.security.casbin-exclude-paths} 中配置，未配置时使用 {@link SecurityProperties} 默认值。
 *
 * @author yunpei
 */
// final + 无 @Bean 互调：必须用 lite 模式，否则 CGLIB 无法增强 final 类
@Configuration(proxyBeanMethods = false)
public final class WebConfig implements WebMvcConfigurer {

    private final Enforcer casbinEnforcer;
    private final LanguageInterceptor languageInterceptor;
    private final SecurityProperties securityProperties;

    public WebConfig(
            Enforcer casbinEnforcer, LanguageInterceptor languageInterceptor, SecurityProperties securityProperties) {
        this.casbinEnforcer = casbinEnforcer;
        this.languageInterceptor = languageInterceptor;
        this.securityProperties = securityProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. Sa-Token 认证拦截器：非排除路径强制登录（全站 /api 认证单一真相源）
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/api/**")
                .excludePathPatterns(securityProperties.getAuthExcludePaths());

        // 2. Language：须在 Sa 之后，才能对已登录用户异步收敛 languageCode
        registry.addInterceptor(languageInterceptor).addPathPatterns("/api/**");

        // 3. jcasbin 授权拦截器（deny-by-default；需先加 policy 才能访问）
        registry.addInterceptor(new CasbinInterceptor(casbinEnforcer))
                .addPathPatterns("/api/**")
                .excludePathPatterns(securityProperties.getCasbinExcludePaths());
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // dev 期 CORS 全放开；prod 应收紧
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}

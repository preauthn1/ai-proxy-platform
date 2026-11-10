package com.yunpei.proxy.infra.satoken;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;

/**
 * Sa-Token 工具配置。
 *
 * <p>Sa-Token 的 {@code SaTokenDao} bean 由 {@code sa-token-spring-boot-starter} 自动装配
 * （在引入 {@code sa-token-redis-template} 依赖后，starter 自动用 {@code SaTokenDaoForRedisTemplate} +
 * Spring Data Redis 的 {@code StringRedisTemplate} 实现），无需我们手工注册。
 * <p>本类只保留静态工具方法 + 占位 {@code @Configuration}（方便业务扩展）。
 * <p>使用 {@code proxyBeanMethods = false}：无 {@code @Bean} 互调，避免 CGLIB 增强；
 * 默认 full 模式会要求可被子类化的构造器，private 构造器会导致启动失败。
 *
 * <p>Q3 决策：<strong>Redis 模式</strong>（不引 JWT）。所有 token 存 Redis。
 *
 * @author yunpei
 */
@Configuration(proxyBeanMethods = false)
@SuppressWarnings("checkstyle:HideUtilityClassConstructor") // Spring 需要可实例化的 lite @Configuration bean
public class SaTokenConfig {

    /**
     * 当前登录用户 id 取值（用于 Logback MDC）。
     */
    public static Long currentUserIdOrNull() {
        try {
            return StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        } catch (Exception e) {
            return null;
        }
    }
}

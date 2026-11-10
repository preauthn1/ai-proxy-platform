package com.yunpei.proxy.service.port;

import java.util.List;

/**
 * Casbin 策略同步端口（service → infra 实现）。
 *
 * <p>业务层在用户-角色变更后通过本端口重算该用户的 p 策略，避免 service 依赖 jcasbin。
 *
 * @author yunpei
 */
public interface CasbinPolicyPort {

    /**
     * 替换指定主体的全部 p 策略。
     *
     * @param subject  主体（用户 ID 字符串）
     * @param policies 新策略列表，每条为 [obj, act]；空列表表示清空（deny-by-default）
     * @param keepWildcard 为 true 时额外写入 path=/*, method=* 通配（Root 兼容）
     */
    void replaceUserPolicies(String subject, List<ApiPolicy> policies, boolean keepWildcard);

    /**
     * 鉴权判断（测试与联调用）。
     */
    boolean enforce(String subject, String obj, String act);

    /**
     * API 路径 + HTTP 方法策略条目。
     *
     * @param path   资源路径（如 /api/system/user/list）
     * @param method HTTP 方法（GET/POST/...）
     */
    record ApiPolicy(String path, String method) {}
}

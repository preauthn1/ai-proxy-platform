package com.yunpei.proxy.infra.casbin;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.casbin.jcasbin.main.Enforcer;
import org.springframework.stereotype.Service;

/**
 * jcasbin 业务封装 Service。
 *
 * <p>提供 policy 的增删查与鉴权判断，供业务代码（Controller / Service）调用。
 * 直接操作 {@link Enforcer}，变更会通过 JDBC Adapter 持久化到 {@code casbin_rule} 表。
 *
 * <p>典型用法：
 * <pre>{@code
 * // 添加策略：用户 1 可 GET /api/system/user/*
 * casbinService.addPolicy("1", "/api/system/user/*", "GET");
 *
 * // 鉴权判断
 * boolean ok = casbinService.enforce("1", "/api/system/user/1", "GET");
 *
 * // 移除策略
 * casbinService.removePolicy("1", "/api/system/user/*", "GET");
 * }</pre>
 *
 * @author yunpei
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CasbinService {

    private final Enforcer enforcer;

    /**
     * 鉴权判断：sub 能否对 obj 执行 act。
     *
     * @param sub 主体（用户 ID 字符串）
     * @param obj 资源（API 路径，支持 keyMatch2 通配符）
     * @param act 动作（HTTP 方法）
     * @return true=允许 false=拒绝
     */
    public boolean enforce(String sub, String obj, String act) {
        return enforcer.enforce(sub, obj, act);
    }

    /**
     * 添加策略。
     *
     * @param sub 主体
     * @param obj 资源
     * @param act 动作
     * @return true=添加成功（policy 不存在时）false=已存在
     */
    public boolean addPolicy(String sub, String obj, String act) {
        boolean ok = enforcer.addPolicy(sub, obj, act);
        if (ok) {
            log.atInfo()
                    .addKeyValue("sub", sub)
                    .addKeyValue("obj", obj)
                    .addKeyValue("act", act)
                    .addKeyValue("logType", "CASBIN")
                    .log("policy added");
        }
        return ok;
    }

    /**
     * 移除策略。
     *
     * @param sub 主体
     * @param obj 资源
     * @param act 动作
     * @return true=移除成功 false=不存在
     */
    public boolean removePolicy(String sub, String obj, String act) {
        boolean ok = enforcer.removePolicy(sub, obj, act);
        if (ok) {
            log.atInfo()
                    .addKeyValue("sub", sub)
                    .addKeyValue("obj", obj)
                    .addKeyValue("act", act)
                    .addKeyValue("logType", "CASBIN")
                    .log("policy removed");
        }
        return ok;
    }

    /**
     * 获取所有策略。
     *
     * @return 策略列表，每条为 [sub, obj, act]
     */
    public List<List<String>> getPolicy() {
        return enforcer.getPolicy();
    }

    /**
     * 按字段过滤策略（fieldIndex=0 时按 sub 过滤）。
     *
     * @param fieldIndex  字段下标（0=sub, 1=obj, 2=act）
     * @param fieldValues 过滤值
     * @return 匹配的策略列表
     */
    public List<List<String>> getFilteredPolicy(int fieldIndex, String... fieldValues) {
        return enforcer.getFilteredPolicy(fieldIndex, fieldValues);
    }

    /**
     * 移除某主体下全部 p 策略（fieldIndex=0 按 sub）。
     *
     * @param subject 主体（用户 ID 字符串）
     * @return 是否移除了至少一条
     */
    public boolean removePoliciesForSubject(String subject) {
        boolean ok = enforcer.removeFilteredPolicy(0, subject);
        if (ok) {
            log.atInfo()
                    .addKeyValue("logType", "CASBIN")
                    .addKeyValue("subject", subject)
                    .log("policies removed");
        }
        return ok;
    }

    /**
     * 重新从数据库加载 policy。
     *
     * <p>当外部直接修改了 {@code casbin_rule} 表时调用，同步内存中的 policy。
     */
    public void reloadPolicy() {
        enforcer.loadPolicy();
        log.atInfo().addKeyValue("logType", "CASBIN").log("policy reloaded from DB");
    }
}

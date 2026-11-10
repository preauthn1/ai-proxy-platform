package com.yunpei.proxy.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.core.api.pagination.EasyPageResult;
import com.yunpei.proxy.service.entity.SysLoginLog;
import com.yunpei.proxy.service.entity.SysLoginLogArchive;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 登录日志 Repository（只增 + 分页查询热表/归档）。
 *
 * @author yunpei
 */
@Component
@RequiredArgsConstructor
public class SysLoginLogRepository {

    private final EasyEntityQuery easyEntityQuery;

    /**
     * 追加一条登录日志。
     *
     * @param log 日志实体
     */
    public void insert(SysLoginLog log) {
        easyEntityQuery.insertable(log).executeRows();
    }

    /**
     * 热表分页：username 模糊、success 精确、loginMethod 精确、loginIp 包含、loginTime 区间；最新优先。
     */
    public EasyPageResult<SysLoginLog> pageHot(
            int page,
            int pageSize,
            String username,
            Integer success,
            String loginMethod,
            String loginIp,
            LocalDateTime loginTimeFrom,
            LocalDateTime loginTimeTo) {
        return easyEntityQuery
                .queryable(SysLoginLog.class)
                .where(t -> {
                    t.username().like(username != null, username);
                    t.success().eq(success != null, success);
                    t.loginMethod().eq(loginMethod != null, loginMethod);
                    t.loginIp().like(loginIp != null, loginIp);
                    t.loginTime().ge(loginTimeFrom != null, loginTimeFrom);
                    t.loginTime().le(loginTimeTo != null, loginTimeTo);
                })
                .orderBy(t -> {
                    t.loginTime().desc();
                    t.id().desc();
                })
                .toPageResult(page, pageSize);
    }

    /** 归档表分页，筛选语义同热表。 */
    public EasyPageResult<SysLoginLogArchive> pageArchive(
            int page,
            int pageSize,
            String username,
            Integer success,
            String loginMethod,
            String loginIp,
            LocalDateTime loginTimeFrom,
            LocalDateTime loginTimeTo) {
        return easyEntityQuery
                .queryable(SysLoginLogArchive.class)
                .where(t -> {
                    t.username().like(username != null, username);
                    t.success().eq(success != null, success);
                    t.loginMethod().eq(loginMethod != null, loginMethod);
                    t.loginIp().like(loginIp != null, loginIp);
                    t.loginTime().ge(loginTimeFrom != null, loginTimeFrom);
                    t.loginTime().le(loginTimeTo != null, loginTimeTo);
                })
                .orderBy(t -> {
                    t.loginTime().desc();
                    t.id().desc();
                })
                .toPageResult(page, pageSize);
    }
}

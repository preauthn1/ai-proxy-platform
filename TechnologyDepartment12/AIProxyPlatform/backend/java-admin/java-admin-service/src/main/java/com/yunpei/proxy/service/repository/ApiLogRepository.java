package com.yunpei.proxy.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.core.api.pagination.EasyPageResult;
import com.yunpei.proxy.service.entity.ApiLog;
import com.yunpei.proxy.service.entity.ApiLogArchive;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * API 调用日志 Repository（只增 + 分页查询热表/归档）。
 *
 * @author yunpei
 */
@Component
@RequiredArgsConstructor
public class ApiLogRepository {

    private final EasyEntityQuery easyEntityQuery;

    public void insert(ApiLog log) {
        easyEntityQuery.insertable(log).executeRows();
    }

    /**
     * 热表分页：method 精确、module/path/username/clientIp/requestId 模糊、success/statusCode 精确、
     * createdAt 区间；最新优先。
     */
    public EasyPageResult<ApiLog> pageHot(
            int page,
            int pageSize,
            String method,
            String module,
            String path,
            Integer success,
            Integer statusCode,
            String username,
            String clientIp,
            String requestId,
            LocalDateTime createdAtFrom,
            LocalDateTime createdAtTo) {
        return easyEntityQuery
                .queryable(ApiLog.class)
                .where(t -> {
                    t.method().eq(method != null, method);
                    t.module().like(module != null, module);
                    t.path().like(path != null, path);
                    t.success().eq(success != null, success);
                    t.statusCode().eq(statusCode != null, statusCode);
                    t.username().like(username != null, username);
                    t.clientIp().like(clientIp != null, clientIp);
                    t.requestId().like(requestId != null, requestId);
                    t.createdAt().ge(createdAtFrom != null, createdAtFrom);
                    t.createdAt().le(createdAtTo != null, createdAtTo);
                })
                .orderBy(t -> {
                    t.createdAt().desc();
                    t.id().desc();
                })
                .toPageResult(page, pageSize);
    }

    /** 归档表分页，筛选语义同热表。 */
    public EasyPageResult<ApiLogArchive> pageArchive(
            int page,
            int pageSize,
            String method,
            String module,
            String path,
            Integer success,
            Integer statusCode,
            String username,
            String clientIp,
            String requestId,
            LocalDateTime createdAtFrom,
            LocalDateTime createdAtTo) {
        return easyEntityQuery
                .queryable(ApiLogArchive.class)
                .where(t -> {
                    t.method().eq(method != null, method);
                    t.module().like(module != null, module);
                    t.path().like(path != null, path);
                    t.success().eq(success != null, success);
                    t.statusCode().eq(statusCode != null, statusCode);
                    t.username().like(username != null, username);
                    t.clientIp().like(clientIp != null, clientIp);
                    t.requestId().like(requestId != null, requestId);
                    t.createdAt().ge(createdAtFrom != null, createdAtFrom);
                    t.createdAt().le(createdAtTo != null, createdAtTo);
                })
                .orderBy(t -> {
                    t.createdAt().desc();
                    t.id().desc();
                })
                .toPageResult(page, pageSize);
    }
}

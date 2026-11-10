package com.yunpei.proxy.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.core.api.pagination.EasyPageResult;
import com.yunpei.proxy.service.entity.TemporalTaskConfig;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 任务配置 Repository。
 *
 * <p>软删过滤由 {@code BaseEntity#deletedAt} 的 {@code @LogicDelete} 自动附加。
 *
 * @author yunpei
 */
@Component
@RequiredArgsConstructor
public class TemporalTaskConfigRepository {

    private final EasyEntityQuery easyEntityQuery;

    public TemporalTaskConfig findById(Long id) {
        return easyEntityQuery
                .queryable(TemporalTaskConfig.class)
                .where(t -> t.id().eq(id))
                .firstOrNull();
    }

    /** 按任务编码查活跃配置（镜像挂 config_id 时用）。 */
    public TemporalTaskConfig findByCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return easyEntityQuery
                .queryable(TemporalTaskConfig.class)
                .where(t -> t.code().eq(code.trim()))
                .firstOrNull();
    }

    public boolean existsByCode(String code, Long excludeId) {
        return easyEntityQuery
                .queryable(TemporalTaskConfig.class)
                .where(t -> {
                    t.code().eq(code);
                    t.id().ne(excludeId != null, excludeId);
                })
                .any();
    }

    public EasyPageResult<TemporalTaskConfig> page(
            int page,
            int pageSize,
            List<String> codeExact,
            String codeLike,
            String name,
            Integer status,
            String workflowType,
            String taskQueue) {
        return easyEntityQuery
                .queryable(TemporalTaskConfig.class)
                .where(t -> {
                    if (codeExact != null && !codeExact.isEmpty()) {
                        t.code().in(codeExact);
                    } else if (codeLike != null) {
                        t.code().like(codeLike);
                    }
                    t.name().like(name != null, name);
                    t.isEnabled().eq(status != null, status);
                    t.workflowType().eq(workflowType != null, workflowType);
                    t.taskQueue().eq(taskQueue != null, taskQueue);
                })
                .orderBy(t -> t.id().asc())
                .toPageResult(page, pageSize);
    }

    public List<TemporalTaskConfig> listByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return easyEntityQuery
                .queryable(TemporalTaskConfig.class)
                .where(t -> t.id().in(ids))
                .orderBy(t -> t.id().asc())
                .toList();
    }

    /**
     * 启动同步用：全部未软删配置（含禁用、无 cron）。
     *
     * <p>@LogicDelete 自动过滤软删行；同步侧据 is_enabled + cron_expr 决定 upsert / pause。
     */
    public List<TemporalTaskConfig> listAllActive() {
        return easyEntityQuery
                .queryable(TemporalTaskConfig.class)
                .orderBy(t -> t.id().asc())
                .toList();
    }

    /** 按 id 批量解析名称（仅未软删配置，@LogicDelete 自动过滤）。 */
    public Map<Long, String> mapNameByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return easyEntityQuery.queryable(TemporalTaskConfig.class).where(t -> t.id().in(ids)).toList().stream()
                .collect(Collectors.toMap(TemporalTaskConfig::getId, TemporalTaskConfig::getName, (a, b) -> a));
    }

    public void insert(TemporalTaskConfig row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long update(TemporalTaskConfig row) {
        return easyEntityQuery.updatable(row).executeRows();
    }

    public long softDeleteById(Long id) {
        return easyEntityQuery
                .deletable(TemporalTaskConfig.class)
                .where(t -> t.id().eq(id))
                .executeRows();
    }

    public long updateIsEnabled(Long id, int isEnabled) {
        return easyEntityQuery
                .updatable(TemporalTaskConfig.class)
                .setColumns(t -> t.isEnabled().set(isEnabled))
                .where(t -> t.id().eq(id))
                .executeRows();
    }
}

package com.yunpei.proxy.service.api;

import com.yunpei.proxy.service.entity.SysApi;
import io.github.linpeilie.annotations.AutoMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * API 资源管理领域模型（service 层，不绑 HTTP 注解）。
 *
 * @author yunpei
 */
public final class ApiManageModels {

    private ApiManageModels() {}

    public static final Set<String> ALLOWED_METHODS =
            Set.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD");

    /** 列表筛选：分页基数为「分组」。 */
    public record ApiListQuery(
            int page, int pageSize, String name, String path, String method, String group, Integer status) {

        public static ApiListQuery of(
                Integer page, Integer pageSize, String name, String path, String method, String group, Integer status) {
            int pageNo = page == null || page < 1 ? 1 : page;
            int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);
            String methodFilter = trimToNull(method);
            if (methodFilter != null && "全部".equals(methodFilter)) {
                methodFilter = null;
            } else if (methodFilter != null) {
                methodFilter = methodFilter.toUpperCase(Locale.ROOT);
            }
            String groupFilter = trimToNull(group);
            if (groupFilter != null && "全部".equals(groupFilter)) {
                groupFilter = null;
            }
            return new ApiListQuery(
                    pageNo, size, trimToNull(name), trimToNull(path), methodFilter, groupFilter, status);
        }

        private static String trimToNull(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }

    public record CreateApiCommand(
            String name,
            String method,
            String path,
            String permissionCode,
            String apiGroup,
            String remark,
            Integer isEnabled) {}

    /** 更新命令；字段 null 表示不改。 */
    public record UpdateApiCommand(
            Long id,
            String name,
            String method,
            String path,
            String permissionCode,
            String apiGroup,
            String remark,
            Integer isEnabled) {}

    /**
     * API 资源视图；{@link AutoMapper} 映射自 {@link SysApi}。
     *
     * <p>紧凑构造器将 null 规范为 "" / 0，与历史 toView 契约一致（record 目标无法用
     * ReverseAutoMapping.defaultValue，因其会生成不可编译的 update 方法）。
     */
    @AutoMapper(target = SysApi.class)
    public record ApiView(
            Long id,
            String name,
            String method,
            String path,
            String permissionCode,
            String apiGroup,
            String remark,
            Integer isEnabled,
            Long deletedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long createdBy,
            Long updatedBy) {
        public ApiView {
            apiGroup = apiGroup == null ? "" : apiGroup;
            remark = remark == null ? "" : remark;
            isEnabled = isEnabled == null ? 0 : isEnabled;
            deletedAt = deletedAt == null ? 0L : deletedAt;
            createdBy = createdBy == null ? 0L : createdBy;
            updatedBy = updatedBy == null ? 0L : updatedBy;
        }
    }

    /**
     * 按组分页结果。
     *
     * @param total     分组总数（分页基数）
     * @param itemTotal 筛选后的接口条数
     */
    public record ApiListPage(List<ApiView> items, long total, long itemTotal) {}

    public record ApiBatchCommand(String action, List<Long> ids) {}

    public record ApiBatchResult(String action, int affected, List<Long> ids) {}

    public record ApiSyncResult(int added, int skipped, int total) {}
}

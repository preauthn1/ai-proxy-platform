package com.yunpei.proxy.service.role;

import com.yunpei.proxy.service.entity.SysRole;
import io.github.linpeilie.annotations.AutoMapper;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色管理领域模型（service 层，不绑 HTTP 注解）。
 *
 * @author yunpei
 */
public final class RoleManageModels {

    private RoleManageModels() {}

    public static final String ROOT_ROLE_CODE = "root";

    /** 列表筛选条件。 */
    public record RoleListQuery(int page, int pageSize, String code, String name, Integer status) {

        public static RoleListQuery of(Integer page, Integer pageSize, String code, String name, Integer status) {
            int pageNo = page == null || page < 1 ? 1 : page;
            int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);
            return new RoleListQuery(pageNo, size, trimToNull(code), trimToNull(name), status);
        }

        private static String trimToNull(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }

    public record CreateRoleCommand(
            String code, String name, Long parentId, Integer sort, Integer isEnabled, String remark) {}

    /**
     * 更新命令；code 不可改。
     *
     * <p>{@code parentId}：使用 {@link ParentIdChange} 区分「未传」与「置为 null」。
     */
    public record UpdateRoleCommand(
            Long id, String name, ParentIdChange parentId, Integer sort, Integer isEnabled, String remark) {}

    /** parentId 变更：absent=不改；present 含 null=设为无父。 */
    public record ParentIdChange(boolean present, Long value) {
        public static ParentIdChange absent() {
            return new ParentIdChange(false, null);
        }

        public static ParentIdChange of(Long value) {
            return new ParentIdChange(true, value);
        }
    }

    /** userCount / parentName 为 enrich，由 Service 在 convert 后补入。 */
    @AutoMapper(target = SysRole.class)
    public record RoleView(
            Long id,
            String code,
            String name,
            Long parentId,
            Integer sort,
            String remark,
            Integer isEnabled,
            Long deletedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long createdBy,
            Long updatedBy,
            Long userCount,
            String parentName) {
        public RoleView {
            sort = sort == null ? 0 : sort;
            remark = remark == null ? "" : remark;
            isEnabled = isEnabled == null ? 0 : isEnabled;
            deletedAt = deletedAt == null ? 0L : deletedAt;
            createdBy = createdBy == null ? 0L : createdBy;
            updatedBy = updatedBy == null ? 0L : updatedBy;
            userCount = userCount == null ? 0L : userCount;
        }
    }

    public record RoleMenuBindView(
            Long id,
            Long parentId,
            String name,
            String type,
            String path,
            String component,
            String icon,
            String redirect,
            String permissionCode,
            String treePath,
            String metadata,
            Integer sort,
            Integer isHidden,
            Integer isEnabled,
            String remark,
            Long deletedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            boolean bound) {}

    public record RoleApiBindView(
            Long id,
            String name,
            String method,
            String path,
            String permissionCode,
            String apiGroup,
            Integer isEnabled,
            boolean bound) {}

    public record RoleMenuBindResult(Long roleId, List<Long> menuIds) {}

    public record RoleApiBindResult(Long roleId, List<Long> apiIds) {}
}

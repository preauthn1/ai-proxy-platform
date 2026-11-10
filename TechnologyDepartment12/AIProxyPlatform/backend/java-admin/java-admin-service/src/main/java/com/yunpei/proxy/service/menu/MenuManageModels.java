package com.yunpei.proxy.service.menu;

import com.yunpei.proxy.service.entity.SysMenu;
import io.github.linpeilie.annotations.AutoMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 菜单管理领域模型（service 层，不绑 HTTP 注解）。
 *
 * @author yunpei
 */
public final class MenuManageModels {

    private MenuManageModels() {}

    public static final String TYPE_DIR = "DIR";
    public static final String TYPE_MENU = "MENU";
    public static final String TYPE_BUTTON = "BUTTON";

    /** 列表筛选（分页按根节点）。 */
    public record MenuListQuery(
            int page, int pageSize, String name, String type, String permissionCode, Integer status) {

        public static MenuListQuery of(
                Integer page, Integer pageSize, String name, String type, String permissionCode, Integer status) {
            int pageNo = page == null || page < 1 ? 1 : page;
            int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);
            String typeFilter = trimToNull(type);
            if (typeFilter != null && "全部".equals(typeFilter)) {
                typeFilter = null;
            }
            return new MenuListQuery(pageNo, size, trimToNull(name), typeFilter, trimToNull(permissionCode), status);
        }

        private static String trimToNull(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }

    public record CreateMenuCommand(
            Long parentId,
            String name,
            String type,
            String path,
            String component,
            String icon,
            String redirect,
            String permissionCode,
            String metadata,
            Integer sort,
            Integer isHidden,
            Integer isEnabled,
            String remark) {}

    /**
     * 更新命令；字段 null 表示不改。
     *
     * <p>{@code parentId} 用 {@link ParentIdChange} 区分「未传」与「置根」。
     */
    public record UpdateMenuCommand(
            Long id,
            ParentIdChange parentId,
            String name,
            String type,
            String path,
            String component,
            String icon,
            String redirect,
            String permissionCode,
            /* 特殊：present=true 才改；value 可为 null。 */
            MetadataChange metadata,
            Integer sort,
            Integer isHidden,
            Integer isEnabled,
            String remark) {}

    public record ParentIdChange(boolean present, Long value) {
        public static ParentIdChange absent() {
            return new ParentIdChange(false, null);
        }

        public static ParentIdChange of(Long value) {
            return new ParentIdChange(true, value);
        }
    }

    public record MetadataChange(boolean present, String value) {
        public static MetadataChange absent() {
            return new MetadataChange(false, null);
        }

        public static MetadataChange of(String value) {
            return new MetadataChange(true, value);
        }
    }

    @AutoMapper(target = SysMenu.class)
    public record MenuView(
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
            Long deletedAt,
            String remark,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long createdBy,
            Long updatedBy) {
        public MenuView {
            icon = icon == null ? "" : icon;
            redirect = redirect == null ? "" : redirect;
            treePath = treePath == null ? "" : treePath;
            remark = remark == null ? "" : remark;
            sort = sort == null ? 0 : sort;
            isHidden = isHidden == null ? 0 : isHidden;
            isEnabled = isEnabled == null ? 0 : isEnabled;
            deletedAt = deletedAt == null ? 0L : deletedAt;
            createdBy = createdBy == null ? 0L : createdBy;
            updatedBy = updatedBy == null ? 0L : updatedBy;
        }
    }

    public record MenuListPage(List<MenuView> items, long total, long itemTotal) {}

    public record MenuBatchCommand(String action, List<Long> ids) {}

    public record MenuBatchResult(String action, int affected, List<Long> ids) {}

    public record MenuApiBindView(
            Long id,
            String name,
            String method,
            String path,
            String permissionCode,
            String apiGroup,
            Integer isEnabled,
            boolean bound) {}

    public record MenuApiBindResult(Long menuId, List<Long> apiIds) {}

    public record ApisByMenusResult(List<Long> menuIds, List<Long> apiIds) {}

    /**
     * 动态路由节点（对齐前端 MenuItem / RuntimeMenuRoute）。
     */
    public record RuntimeMenuRoute(
            String name,
            String path,
            String component,
            String redirect,
            Map<String, Object> meta,
            List<RuntimeMenuRoute> children) {}
}

package com.yunpei.proxy.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.yunpei.proxy.service.entity.SysMenu;
import com.yunpei.proxy.service.entity.proxy.SysMenuProxy;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 系统菜单 Repository。
 *
 * <p>软删过滤由 {@code BaseEntity#deletedAt} 的 {@code @LogicDelete} 自动附加。
 *
 * @author yunpei
 */
@Component
@RequiredArgsConstructor
public class SysMenuRepository {

    private final EasyEntityQuery easyEntityQuery;

    public SysMenu findById(Long id) {
        return easyEntityQuery
                .queryable(SysMenu.class)
                .where(m -> m.id().eq(id))
                .firstOrNull();
    }

    /** 全量未软删菜单，按 sort/id 升序。 */
    public List<SysMenu> listAll() {
        return easyEntityQuery
                .queryable(SysMenu.class)
                .orderBy(m -> {
                    m.sort().asc();
                    m.id().asc();
                })
                .toList();
    }

    /**
     * 全量未软删菜单，可选 type / status 过滤。
     *
     * @param type   DIR|MENU|BUTTON；null 不过滤
     * @param status is_enabled 0|1；null 不过滤
     */
    public List<SysMenu> listAll(String type, Integer status) {
        return easyEntityQuery
                .queryable(SysMenu.class)
                .where(m -> {
                    m.type().eq(type != null, type);
                    m.isEnabled().eq(status != null, status);
                })
                .orderBy(m -> {
                    m.sort().asc();
                    m.id().asc();
                })
                .toList();
    }

    public boolean existsById(Long id) {
        return easyEntityQuery
                .queryable(SysMenu.class)
                .where(m -> m.id().eq(id))
                .any();
    }

    /**
     * name 是否已被其他未软删菜单占用。
     *
     * @param excludeId 更新时排除自身；null 表示不排除
     */
    public boolean existsByName(String name, Long excludeId) {
        return easyEntityQuery
                .queryable(SysMenu.class)
                .where(m -> {
                    m.name().eq(name);
                    m.id().ne(excludeId != null, excludeId);
                })
                .any();
    }

    /**
     * path 是否已被其他未软删菜单占用（path 非空时才有意义）。
     */
    public boolean existsByPath(String path, Long excludeId) {
        if (path == null || path.isBlank()) {
            return false;
        }
        return easyEntityQuery
                .queryable(SysMenu.class)
                .where(m -> {
                    m.path().eq(path);
                    m.id().ne(excludeId != null, excludeId);
                })
                .any();
    }

    /** 是否存在未软删的直接子节点。 */
    public boolean hasChildren(Long parentId) {
        return easyEntityQuery
                .queryable(SysMenu.class)
                .where(m -> m.parentId().eq(parentId))
                .any();
    }

    /**
     * 查询 tree_path 以给定前缀开头的未软删节点（含自身时由调用方决定前缀）。
     */
    public List<SysMenu> listByTreePathPrefix(String treePathPrefix) {
        return easyEntityQuery
                .queryable(SysMenu.class)
                .where(m -> m.treePath().likeMatchLeft(treePathPrefix))
                .toList();
    }

    public void insert(SysMenu menu) {
        easyEntityQuery.insertable(menu).executeRows(true);
    }

    public long update(SysMenu menu) {
        return easyEntityQuery.updatable(menu).executeRows();
    }

    public long softDeleteById(Long id) {
        return easyEntityQuery
                .deletable(SysMenu.class)
                .where(m -> m.id().eq(id))
                .executeRows();
    }

    public long updateIsEnabled(Long id, int isEnabled) {
        return easyEntityQuery
                .updatable(SysMenu.class)
                .setColumns(m -> m.isEnabled().set(isEnabled))
                .where(m -> m.id().eq(id))
                .executeRows();
    }

    /** 批量更新 tree_path（对象更新）。 */
    public void updateTreePath(Long id, String treePath) {
        easyEntityQuery
                .updatable(SysMenu.class)
                .setColumns(m -> m.treePath().set(treePath))
                .where(m -> m.id().eq(id))
                .executeRows();
    }

    /** 按 ID 列表查询未软删菜单。 */
    public List<SysMenu> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return easyEntityQuery
                .queryable(SysMenu.class)
                .where(m -> m.id().in(ids))
                .toList();
    }

    /** 查询 id 列表中存在的未软删主键。 */
    public List<Long> findExistingIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return easyEntityQuery
                .queryable(SysMenu.class)
                .where(m -> m.id().in(ids))
                .select(SysMenuProxy::id)
                .toList();
    }
}

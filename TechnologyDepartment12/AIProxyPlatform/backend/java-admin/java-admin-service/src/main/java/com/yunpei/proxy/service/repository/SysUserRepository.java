package com.yunpei.proxy.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.core.api.pagination.EasyPageResult;
import com.yunpei.proxy.service.entity.SysUser;
import com.yunpei.proxy.service.entity.SysUserRole;
import com.yunpei.proxy.service.entity.proxy.SysUserRoleProxy;
import com.yunpei.proxy.service.user.UserManageModels.UserListQuery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 系统用户 Repository。
 *
 * <p>基于 Easy-Query 的 {@link EasyEntityQuery} 实现；不引入 Spring Data 接口。
 * 软删过滤由 {@code BaseEntity#deletedAt} 上的 {@code @LogicDelete} 自动附加，
 * 无需在 where 中手写 {@code deleted_at = 0}。
 *
 * @author yunpei
 */
@Component
@RequiredArgsConstructor
public class SysUserRepository {

    private final EasyEntityQuery easyEntityQuery;

    /**
     * 根据用户名查询未软删用户。
     *
     * @param username 用户名
     * @return 用户实体，未找到返回 {@code null}
     */
    public SysUser findByUsername(String username) {
        return easyEntityQuery
                .queryable(SysUser.class)
                .where(u -> u.username().eq(username))
                .firstOrNull();
    }

    /**
     * 根据 ID 查询未软删用户。
     *
     * @param id 主键
     * @return 用户实体，未找到返回 {@code null}
     */
    public SysUser findById(Long id) {
        return easyEntityQuery
                .queryable(SysUser.class)
                .where(u -> u.id().eq(id))
                .firstOrNull();
    }

    /**
     * 用户名是否已被占用（未软删）。
     */
    public boolean existsByUsername(String username) {
        return easyEntityQuery
                .queryable(SysUser.class)
                .where(u -> u.username().eq(username))
                .any();
    }

    /**
     * 分页查询用户；可选 username/nickname 模糊、status、roleId 过滤。
     */
    public EasyPageResult<SysUser> page(UserListQuery query) {
        return easyEntityQuery
                .queryable(SysUser.class)
                .where(u -> {
                    u.username().like(query.username() != null, query.username());
                    u.nickname().like(query.nickname() != null, query.nickname());
                    u.isEnabled().eq(query.status() != null, query.status());
                    if (query.roleId() != null) {
                        List<Long> userIds = easyEntityQuery
                                .queryable(SysUserRole.class)
                                .where(ur -> ur.roleId().eq(query.roleId()))
                                .select(SysUserRoleProxy::userId)
                                .toList();
                        if (userIds.isEmpty()) {
                            u.id().eq(-1L);
                        } else {
                            u.id().in(userIds);
                        }
                    }
                })
                .orderBy(u -> u.id().asc())
                .toPageResult(query.page(), query.pageSize());
    }

    /**
     * 插入用户；回填自增主键。
     */
    public void insert(SysUser user) {
        easyEntityQuery.insertable(user).executeRows(true);
    }

    /**
     * 按主键更新实体（审计字段由拦截器填充）。
     */
    public long update(SysUser user) {
        return easyEntityQuery.updatable(user).executeRows();
    }

    /**
     * 软删用户（LogicDelete 改写为 UPDATE deleted_at）。
     */
    public long softDeleteById(Long id) {
        return easyEntityQuery
                .deletable(SysUser.class)
                .where(u -> u.id().eq(id))
                .executeRows();
    }

    /**
     * 更新密码哈希。
     */
    public long updatePasswordHash(Long id, String passwordHash) {
        return easyEntityQuery
                .updatable(SysUser.class)
                .setColumns(u -> u.passwordHash().set(passwordHash))
                .where(u -> u.id().eq(id))
                .executeRows();
    }

    /**
     * 更新启停状态。
     */
    public long updateIsEnabled(Long id, int isEnabled) {
        return easyEntityQuery
                .updatable(SysUser.class)
                .setColumns(u -> u.isEnabled().set(isEnabled))
                .where(u -> u.id().eq(id))
                .executeRows();
    }

    /**
     * 更新用户默认语言码。
     *
     * @param userId       用户主键
     * @param languageCode 语言码（如 zh-CN）
     * @return 影响行数
     */
    public long updateLanguageCode(Long userId, String languageCode) {
        return easyEntityQuery
                .updatable(SysUser.class)
                .setColumns(u -> u.languageCode().set(languageCode))
                .where(u -> u.id().eq(userId))
                .executeRows();
    }
}

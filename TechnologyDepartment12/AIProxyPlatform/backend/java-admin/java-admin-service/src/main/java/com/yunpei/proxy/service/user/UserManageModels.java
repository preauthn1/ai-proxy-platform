package com.yunpei.proxy.service.user;

import com.yunpei.proxy.service.entity.SysUser;
import io.github.linpeilie.annotations.AutoMapper;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户管理领域模型（service 层，不绑 HTTP 注解）。
 *
 * @author yunpei
 */
public final class UserManageModels {

    private UserManageModels() {}

    /** 列表筛选条件。 */
    public record UserListQuery(int page, int pageSize, String username, String nickname, Integer status, Long roleId) {

        public static UserListQuery of(
                Integer page, Integer pageSize, String username, String nickname, Integer status, Long roleId) {
            int pageNo = page == null || page < 1 ? 1 : page;
            int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);
            return new UserListQuery(pageNo, size, trimToNull(username), trimToNull(nickname), status, roleId);
        }

        private static String trimToNull(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }

    /** 创建命令。 */
    public record CreateUserCommand(
            String username,
            String password,
            String nickname,
            String email,
            String phone,
            String avatar,
            String languageCode,
            Integer isEnabled,
            String remark,
            List<Long> roleIds) {}

    /** 更新命令（username/password 不可改）。 */
    public record UpdateUserCommand(
            Long id,
            String nickname,
            String email,
            String phone,
            String avatar,
            String languageCode,
            Integer isEnabled,
            String remark,
            /* null=不改角色；非 null（可为空列表）=全量替换。 */
            List<Long> roleIds) {}

    /**
     * 对外用户视图（无 passwordHash）。
     *
     * <p>roleIds / roleNames 为 enrich，由 Service 在 convert 后补入。
     */
    @AutoMapper(target = SysUser.class)
    public record UserView(
            Long id,
            String username,
            String nickname,
            String email,
            String phone,
            String avatar,
            String languageCode,
            LocalDateTime lastLoginAt,
            String lastLoginIp,
            String remark,
            Integer isEnabled,
            Long deletedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<Long> roleIds,
            List<String> roleNames) {
        public UserView {
            email = email == null ? "" : email;
            phone = phone == null ? "" : phone;
            avatar = avatar == null ? "" : avatar;
            lastLoginIp = lastLoginIp == null ? "" : lastLoginIp;
            remark = remark == null ? "" : remark;
            isEnabled = isEnabled == null ? 0 : isEnabled;
            deletedAt = deletedAt == null ? 0L : deletedAt;
            roleIds = roleIds == null ? List.of() : List.copyOf(roleIds);
            roleNames = roleNames == null ? List.of() : List.copyOf(roleNames);
        }
    }
}

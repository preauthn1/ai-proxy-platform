package com.yunpei.proxy.service.user;

import com.easy.query.core.api.pagination.EasyPageResult;
import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.service.entity.SysUser;
import com.yunpei.proxy.service.port.CasbinPolicyPort;
import com.yunpei.proxy.service.port.CasbinPolicyPort.ApiPolicy;
import com.yunpei.proxy.service.repository.SysUserRepository;
import com.yunpei.proxy.service.repository.SysUserRoleRepository;
import com.yunpei.proxy.service.user.UserManageModels.CreateUserCommand;
import com.yunpei.proxy.service.user.UserManageModels.UpdateUserCommand;
import com.yunpei.proxy.service.user.UserManageModels.UserListQuery;
import com.yunpei.proxy.service.user.UserManageModels.UserView;
import io.github.linpeilie.Converter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 系统用户 Service：分页查询、CRUD/软删、启停、重置密码、角色分配与 Casbin 同步。
 *
 * @author yunpei
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserService {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final SysUserRepository sysUserRepository;
    private final SysUserRoleRepository sysUserRoleRepository;
    private final CasbinPolicyPort casbinPolicyPort;
    private final Converter converter;

    /** 根据主键查询用户；找不到返回 {@code null}。 */
    public SysUser findById(Long id) {
        return sysUserRepository.findById(id);
    }

    /** 根据用户名查询用户；找不到返回 {@code null}。 */
    public SysUser findByUsername(String username) {
        return sysUserRepository.findByUsername(username);
    }

    /**
     * 分页列表（含 roleIds / roleNames；不回显 passwordHash）。
     */
    public PageData<UserView> pageUsers(UserListQuery query) {
        EasyPageResult<SysUser> page = sysUserRepository.page(query);
        List<SysUser> rows = page.getData() == null ? List.of() : page.getData();
        List<Long> userIds = rows.stream().map(SysUser::getId).toList();
        Map<Long, List<Long>> roleMap = sysUserRoleRepository.findRoleIdsByUserIds(userIds);
        List<Long> allRoleIds =
                roleMap.values().stream().flatMap(List::stream).distinct().toList();
        Map<Long, String> roleNames = sysUserRoleRepository.findRoleNamesByIds(allRoleIds);

        List<UserView> items = new ArrayList<>(rows.size());
        for (SysUser row : rows) {
            List<Long> roleIds = roleMap.getOrDefault(row.getId(), List.of());
            items.add(toView(row, roleIds, roleNames));
        }
        return PageData.of(items, page.getTotal());
    }

    /**
     * 创建用户；密码 BCrypt 存储；写 sys_user_role 并同步 Casbin。
     */
    public UserView create(CreateUserCommand cmd) {
        String username = requireNonBlank(cmd.username(), "username");
        String password = requireNonBlank(cmd.password(), "password");
        String nickname = requireNonBlank(cmd.nickname(), "nickname");
        if (sysUserRepository.existsByUsername(username)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "用户名 " + username + " 已存在");
        }
        List<Long> roleIds = validateRoleIds(cmd.roleIds());

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(PASSWORD_ENCODER.encode(password));
        user.setNickname(nickname);
        user.setEmail(nullToEmpty(cmd.email()));
        user.setPhone(nullToEmpty(cmd.phone()));
        user.setAvatar(nullToEmpty(cmd.avatar()));
        user.setLanguageCode(blankToNull(cmd.languageCode()));
        user.setLastLoginIp("");
        user.setRemark(nullToEmpty(cmd.remark()));
        user.setIsEnabled(cmd.isEnabled() == null ? 1 : (cmd.isEnabled() == 0 ? 0 : 1));

        sysUserRepository.insert(user);
        sysUserRoleRepository.replaceUserRoles(user.getId(), roleIds);
        syncCasbinForUser(user.getId());

        return loadView(user.getId());
    }

    /**
     * 更新用户基本信息；若提供 roleIds 则替换并同步 Casbin。
     * username/password 不可通过本接口修改。
     */
    public UserView update(UpdateUserCommand cmd) {
        SysUser user = requireUser(cmd.id());
        applyProfileUpdates(user, cmd);
        sysUserRepository.update(user);

        if (cmd.roleIds() != null) {
            List<Long> roleIds = validateRoleIds(cmd.roleIds());
            sysUserRoleRepository.replaceUserRoles(user.getId(), roleIds);
            syncCasbinForUser(user.getId());
        }
        return loadView(user.getId());
    }

    private static void applyProfileUpdates(SysUser user, UpdateUserCommand cmd) {
        if (cmd.nickname() != null) {
            String nickname = cmd.nickname().trim();
            if (nickname.isEmpty()) {
                throw BizException.of(ResultCode.PARAM_INVALID, "nickname 不能为空");
            }
            user.setNickname(nickname);
        }
        if (cmd.email() != null) {
            user.setEmail(cmd.email().trim());
        }
        if (cmd.phone() != null) {
            user.setPhone(cmd.phone().trim());
        }
        if (cmd.avatar() != null) {
            user.setAvatar(cmd.avatar().trim());
        }
        if (cmd.languageCode() != null) {
            user.setLanguageCode(blankToNull(cmd.languageCode()));
        }
        if (cmd.isEnabled() != null) {
            user.setIsEnabled(cmd.isEnabled() == 0 ? 0 : 1);
        }
        if (cmd.remark() != null) {
            user.setRemark(cmd.remark().trim());
        }
    }

    /**
     * 软删用户；清空 sys_user_role 与该用户 Casbin 策略。
     */
    public UserView softDelete(Long id) {
        SysUser user = requireUser(id);
        List<Long> roleIds = sysUserRoleRepository.findRoleIdsByUserId(id);
        Map<Long, String> roleNameMap = sysUserRoleRepository.findRoleNamesByIds(roleIds);

        sysUserRoleRepository.clearUserRoles(id);
        casbinPolicyPort.replaceUserPolicies(String.valueOf(id), List.of(), false);
        long rows = sysUserRepository.softDeleteById(id);
        if (rows == 0) {
            throw BizException.of(ResultCode.PARAM_INVALID, "用户 " + id + " 不存在");
        }
        // 逻辑删除时间由 EQ 写入；响应快照使用当前毫秒，避免仍回 0
        long deletedAt = System.currentTimeMillis();
        user.setDeletedAt(deletedAt);
        return toView(user, roleIds, roleNameMap);
    }

    /**
     * 启停账号：status 0|1。
     */
    public UserView toggleStatus(Long id, int status) {
        if (status != 0 && status != 1) {
            throw BizException.of(ResultCode.PARAM_INVALID, "status 必须为 0 或 1");
        }
        requireUser(id);
        long rows = sysUserRepository.updateIsEnabled(id, status);
        if (rows == 0) {
            throw BizException.of(ResultCode.PARAM_INVALID, "用户 " + id + " 不存在");
        }
        return loadView(id);
    }

    /**
     * 重置密码（BCrypt）；不回显 hash。
     */
    public Long resetPassword(Long id, String password) {
        String pwd = requireNonBlank(password, "password");
        requireUser(id);
        long rows = sysUserRepository.updatePasswordHash(id, PASSWORD_ENCODER.encode(pwd));
        if (rows == 0) {
            throw BizException.of(ResultCode.PARAM_INVALID, "用户 " + id + " 不存在");
        }
        return id;
    }

    /**
     * 按用户角色重算 Casbin p 策略；拥有 root 角色时保留通配。
     */
    public void syncCasbinForUser(Long userId) {
        boolean keepWildcard = sysUserRoleRepository.userHasRootRole(userId);
        List<ApiPolicy> policies = keepWildcard ? List.of() : sysUserRoleRepository.findApiPoliciesByUserId(userId);
        casbinPolicyPort.replaceUserPolicies(String.valueOf(userId), policies, keepWildcard);
        log.atInfo()
                .addKeyValue("userId", userId)
                .addKeyValue("keepWildcard", keepWildcard)
                .addKeyValue("policyCount", policies.size())
                .addKeyValue("logType", "USER")
                .log("casbin synced");
    }

    private UserView loadView(Long userId) {
        SysUser user = requireUser(userId);
        List<Long> roleIds = sysUserRoleRepository.findRoleIdsByUserId(userId);
        Map<Long, String> roleNames = sysUserRoleRepository.findRoleNamesByIds(roleIds);
        return toView(user, roleIds, roleNames);
    }

    private SysUser requireUser(Long id) {
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "id 不能为空");
        }
        SysUser user = sysUserRepository.findById(id);
        if (user == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "用户 " + id + " 不存在");
        }
        return user;
    }

    private List<Long> validateRoleIds(List<Long> roleIds) {
        List<Long> filtered = sysUserRoleRepository.filterExistingRoleIds(roleIds);
        if (filtered == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "角色不存在");
        }
        return filtered;
    }

    private UserView toView(SysUser user, List<Long> roleIds, Map<Long, String> roleNameMap) {
        List<Long> ids = roleIds == null ? List.of() : List.copyOf(roleIds);
        List<String> names = new ArrayList<>();
        for (Long rid : ids) {
            String name = roleNameMap.get(rid);
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        UserView base = converter.convert(user, UserView.class);
        return new UserView(
                base.id(),
                base.username(),
                base.nickname(),
                base.email(),
                base.phone(),
                base.avatar(),
                base.languageCode(),
                base.lastLoginAt(),
                base.lastLoginIp(),
                base.remark(),
                base.isEnabled(),
                base.deletedAt(),
                base.createdAt(),
                base.updatedAt(),
                ids,
                names);
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, field + " 不能为空");
        }
        return value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

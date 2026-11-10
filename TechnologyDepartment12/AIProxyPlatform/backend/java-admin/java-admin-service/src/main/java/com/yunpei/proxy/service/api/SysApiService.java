package com.yunpei.proxy.service.api;

import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.service.api.ApiManageModels.ApiBatchCommand;
import com.yunpei.proxy.service.api.ApiManageModels.ApiBatchResult;
import com.yunpei.proxy.service.api.ApiManageModels.ApiListPage;
import com.yunpei.proxy.service.api.ApiManageModels.ApiListQuery;
import com.yunpei.proxy.service.api.ApiManageModels.ApiSyncResult;
import com.yunpei.proxy.service.api.ApiManageModels.ApiView;
import com.yunpei.proxy.service.api.ApiManageModels.CreateApiCommand;
import com.yunpei.proxy.service.api.ApiManageModels.UpdateApiCommand;
import com.yunpei.proxy.service.entity.SysApi;
import com.yunpei.proxy.service.repository.SysApiRepository;
import com.yunpei.proxy.service.repository.SysMenuApiRepository;
import com.yunpei.proxy.service.repository.SysRoleBindingRepository;
import com.yunpei.proxy.service.repository.SysUserRoleRepository;
import com.yunpei.proxy.service.user.SysUserService;
import io.github.linpeilie.Converter;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 系统 API 资源 Service：按组分页、CRUD/软删、batch、清单同步。
 *
 * @author yunpei
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysApiService {

    private final SysApiRepository apiRepository;
    private final SysMenuApiRepository menuApiRepository;
    private final SysRoleBindingRepository roleBindingRepository;
    private final SysUserRoleRepository userRoleRepository;
    private final SysUserService sysUserService;
    private final Converter converter;

    /**
     * 分页列出接口：分页基数为 api_group；items 为当前页各组下的全部接口（扁平）。
     */
    public ApiListPage pageApis(ApiListQuery query) {
        List<SysApi> filtered =
                apiRepository.listFiltered(query.name(), query.path(), query.method(), query.group(), query.status());

        Map<String, List<SysApi>> groupMap = new LinkedHashMap<>();
        for (SysApi a : filtered) {
            String g = normalizeGroupLabel(a.getApiGroup());
            groupMap.computeIfAbsent(g, k -> new ArrayList<>()).add(a);
        }

        Collator collator = Collator.getInstance(Locale.CHINA);
        List<String> groupNames = groupMap.keySet().stream().sorted(collator).toList();

        int from = Math.max(0, (query.page() - 1) * query.pageSize());
        int to = Math.min(groupNames.size(), from + query.pageSize());
        List<String> pageGroups = from >= groupNames.size() ? List.of() : groupNames.subList(from, to);

        List<ApiView> items = new ArrayList<>();
        for (String g : pageGroups) {
            List<SysApi> apis = groupMap.getOrDefault(g, List.of());
            apis.sort(Comparator.comparing(SysApi::getId));
            for (SysApi a : apis) {
                items.add(converter.convert(a, ApiView.class));
            }
        }
        return new ApiListPage(items, groupNames.size(), filtered.size());
    }

    /** 全量未软删接口。 */
    public List<ApiView> listAll() {
        return converter.convert(apiRepository.listAll(), ApiView.class);
    }

    /** 去重分组名（非空），供下拉。 */
    public List<String> listGroups() {
        return apiRepository.listDistinctGroups();
    }

    public ApiView create(CreateApiCommand cmd) {
        String name = requireNonBlank(cmd.name(), "name");
        String method = requireAllowedMethod(cmd.method());
        String path = requireNonBlank(cmd.path(), "path");
        String permissionCode = requireNonBlank(cmd.permissionCode(), "permissionCode");
        String apiGroup = nullToEmpty(cmd.apiGroup()).trim();

        assertUniqueMethodPath(method, path, null);
        assertUniquePermissionCode(permissionCode, null);

        SysApi api = new SysApi();
        api.setName(name);
        api.setMethod(method);
        api.setPath(path);
        api.setPermissionCode(permissionCode);
        api.setApiGroup(apiGroup);
        api.setRemark(nullToEmpty(cmd.remark()).trim());
        api.setIsEnabled(cmd.isEnabled() == null ? 1 : (cmd.isEnabled() == 0 ? 0 : 1));

        apiRepository.insert(api);
        return loadView(api.getId());
    }

    public ApiView update(UpdateApiCommand cmd) {
        SysApi api = requireApi(cmd.id());
        boolean routeChanged = false;
        boolean enabledChanged = false;
        Integer previousEnabled = api.getIsEnabled();

        if (cmd.method() != null) {
            String method = requireAllowedMethod(cmd.method());
            if (!Objects.equals(method, api.getMethod())) {
                api.setMethod(method);
                routeChanged = true;
            }
        }
        if (cmd.path() != null) {
            String path = cmd.path().trim();
            if (path.isEmpty()) {
                throw BizException.of(ResultCode.PARAM_INVALID, "path cannot be empty");
            }
            if (!Objects.equals(path, api.getPath())) {
                api.setPath(path);
                routeChanged = true;
            }
        }
        if (cmd.permissionCode() != null) {
            String code = cmd.permissionCode().trim();
            if (code.isEmpty()) {
                throw BizException.of(ResultCode.PARAM_INVALID, "permissionCode cannot be empty");
            }
            api.setPermissionCode(code);
        }
        if (cmd.name() != null) {
            api.setName(cmd.name().trim());
        }
        if (cmd.apiGroup() != null) {
            api.setApiGroup(cmd.apiGroup().trim());
        }
        if (cmd.remark() != null) {
            api.setRemark(cmd.remark());
        }
        if (cmd.isEnabled() != null) {
            int enabled = cmd.isEnabled() == 0 ? 0 : 1;
            if (!Objects.equals(enabled, previousEnabled)) {
                api.setIsEnabled(enabled);
                enabledChanged = true;
            }
        }

        assertUniqueMethodPath(api.getMethod(), api.getPath(), api.getId());
        assertUniquePermissionCode(api.getPermissionCode(), api.getId());

        apiRepository.update(api);

        if (routeChanged || enabledChanged) {
            syncCasbinForApi(api.getId());
        }
        return loadView(api.getId());
    }

    /**
     * 软删接口：清 menu_api / role_api 后软删，并对曾绑定角色的用户重算 Casbin。
     */
    public ApiView softDelete(Long id) {
        SysApi api = requireApi(id);
        ApiView snapshot = converter.convert(api, ApiView.class);

        List<Long> roleIds = roleBindingRepository.findRoleIdsByApiId(id);
        menuApiRepository.clearByApiId(id);
        roleBindingRepository.clearApisByApiId(id);
        long rows = apiRepository.softDeleteById(id);
        if (rows == 0) {
            throw BizException.of(ResultCode.PARAM_INVALID, "api " + id + " not found");
        }
        syncCasbinForRoles(roleIds);

        long deletedAt = System.currentTimeMillis();
        return new ApiView(
                snapshot.id(),
                snapshot.name(),
                snapshot.method(),
                snapshot.path(),
                snapshot.permissionCode(),
                snapshot.apiGroup(),
                snapshot.remark(),
                snapshot.isEnabled(),
                deletedAt,
                snapshot.createdAt(),
                snapshot.updatedAt(),
                snapshot.createdBy(),
                snapshot.updatedBy());
    }

    public ApiBatchResult batch(ApiBatchCommand cmd) {
        String action = cmd.action() == null ? "" : cmd.action().trim();
        if (!Set.of("enable", "disable", "delete").contains(action)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "action must be enable|disable|delete");
        }
        List<Long> ids = normalizeIds(cmd.ids());
        if (ids.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "ids must be a non-empty number[]");
        }
        List<SysApi> targets = apiRepository.listByIds(ids);
        if (targets.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "no active api found for given ids");
        }

        if ("delete".equals(action)) {
            List<Long> deleted = new ArrayList<>();
            Set<Long> affectedRoles = new LinkedHashSet<>();
            for (SysApi t : targets) {
                affectedRoles.addAll(roleBindingRepository.findRoleIdsByApiId(t.getId()));
                menuApiRepository.clearByApiId(t.getId());
                roleBindingRepository.clearApisByApiId(t.getId());
                apiRepository.softDeleteById(t.getId());
                deleted.add(t.getId());
            }
            syncCasbinForRoles(new ArrayList<>(affectedRoles));
            return new ApiBatchResult(action, deleted.size(), deleted);
        }

        int enabled = "enable".equals(action) ? 1 : 0;
        List<Long> affected = new ArrayList<>();
        for (SysApi t : targets) {
            apiRepository.updateIsEnabled(t.getId(), enabled);
            affected.add(t.getId());
            syncCasbinForApi(t.getId());
        }
        return new ApiBatchResult(action, affected.size(), affected);
    }

    /**
     * 按内置清单 upsert：命中 (method, path) 则 skip，否则新增。
     *
     * <p>幂等：对已 seed 的库重复调用 added=0。permission_code 冲突时追加 path 消歧后缀。
     */
    public ApiSyncResult syncFromManifest() {
        List<ApiSyncManifest.Entry> manifest = ApiSyncManifest.entries();
        int added = 0;
        int skipped = 0;
        for (ApiSyncManifest.Entry item : manifest) {
            String method = item.method().toUpperCase(Locale.ROOT);
            String path = item.path();
            if (apiRepository.existsByMethodAndPath(method, path, null)) {
                skipped += 1;
                continue;
            }
            String permissionCode = resolvePermissionCode(item.permissionCode(), path);
            SysApi api = new SysApi();
            api.setName(item.name());
            api.setMethod(method);
            api.setPath(path);
            api.setPermissionCode(permissionCode);
            api.setApiGroup(nullToEmpty(item.apiGroup()));
            api.setRemark("同步自后端路由清单");
            api.setIsEnabled(1);
            apiRepository.insert(api);
            added += 1;
        }
        int total = apiRepository.listAll().size();
        log.atInfo()
                .addKeyValue("added", added)
                .addKeyValue("skipped", skipped)
                .addKeyValue("total", total)
                .addKeyValue("logType", "API")
                .log("sync complete");
        return new ApiSyncResult(added, skipped, total);
    }

    private String resolvePermissionCode(String preferred, String path) {
        if (!apiRepository.existsByPermissionCode(preferred, null)) {
            return preferred;
        }
        String slug =
                path.replaceAll("[^a-zA-Z0-9]+", "_").replaceAll("^_|_$", "").toLowerCase(Locale.ROOT);
        if (slug.isEmpty()) {
            slug = "x";
        }
        String candidate = preferred + "__" + slug;
        if (candidate.length() > 128) {
            candidate = candidate.substring(0, 128);
        }
        if (!apiRepository.existsByPermissionCode(candidate, null)) {
            return candidate;
        }
        int i = 2;
        while (true) {
            String withIndex = candidate;
            String suffix = "_" + i;
            if (withIndex.length() + suffix.length() > 128) {
                withIndex = withIndex.substring(0, 128 - suffix.length());
            }
            String next = withIndex + suffix;
            if (!apiRepository.existsByPermissionCode(next, null)) {
                return next;
            }
            i += 1;
        }
    }

    private void syncCasbinForApi(Long apiId) {
        List<Long> roleIds = roleBindingRepository.findRoleIdsByApiId(apiId);
        syncCasbinForRoles(roleIds);
    }

    private void syncCasbinForRoles(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        Set<Long> userIds = new LinkedHashSet<>();
        for (Long roleId : roleIds) {
            userIds.addAll(userRoleRepository.findActiveUserIdsByRoleId(roleId));
        }
        for (Long userId : userIds) {
            sysUserService.syncCasbinForUser(userId);
        }
        log.atInfo()
                .addKeyValue("roles", roleIds.size())
                .addKeyValue("users", userIds.size())
                .addKeyValue("logType", "API")
                .log("casbin synced");
    }

    private ApiView loadView(Long id) {
        return converter.convert(requireApi(id), ApiView.class);
    }

    private SysApi requireApi(Long id) {
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "id 不能为空");
        }
        SysApi api = apiRepository.findById(id);
        if (api == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "api " + id + " not found");
        }
        return api;
    }

    private void assertUniqueMethodPath(String method, String path, Long excludeId) {
        if (apiRepository.existsByMethodAndPath(method, path, excludeId)) {
            throw BizException.of(ResultCode.PARAM_INVALID, method + " " + path + " 已存在");
        }
    }

    private void assertUniquePermissionCode(String permissionCode, Long excludeId) {
        if (apiRepository.existsByPermissionCode(permissionCode, excludeId)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "permissionCode " + permissionCode + " 已存在");
        }
    }

    private static String normalizeGroupLabel(String apiGroup) {
        if (apiGroup == null || apiGroup.isBlank()) {
            return "未分组";
        }
        return apiGroup.trim();
    }

    private static String requireAllowedMethod(String method) {
        String m = requireNonBlank(method, "method").toUpperCase(Locale.ROOT);
        if (!ApiManageModels.ALLOWED_METHODS.contains(m)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "method must be GET/POST/PUT/DELETE/PATCH/OPTIONS/HEAD");
        }
        return m;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw BizException.of(ResultCode.PARAM_INVALID, field + " is required");
        }
        return value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static List<Long> normalizeIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
    }
}

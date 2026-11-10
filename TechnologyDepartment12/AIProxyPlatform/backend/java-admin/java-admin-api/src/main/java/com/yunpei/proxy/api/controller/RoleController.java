package com.yunpei.proxy.api.controller;

import com.yunpei.proxy.api.dto.CreateRoleRequest;
import com.yunpei.proxy.api.dto.RoleApiBindRequest;
import com.yunpei.proxy.api.dto.RoleMenuBindRequest;
import com.yunpei.proxy.api.dto.UpdateRoleRequest;
import com.yunpei.proxy.api.vo.RoleApiBindItemVO;
import com.yunpei.proxy.api.vo.RoleApiBindResultVO;
import com.yunpei.proxy.api.vo.RoleListItemVO;
import com.yunpei.proxy.api.vo.RoleMenuBindItemVO;
import com.yunpei.proxy.api.vo.RoleMenuBindResultVO;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.service.role.RoleManageModels.CreateRoleCommand;
import com.yunpei.proxy.service.role.RoleManageModels.ParentIdChange;
import com.yunpei.proxy.service.role.RoleManageModels.RoleApiBindResult;
import com.yunpei.proxy.service.role.RoleManageModels.RoleListQuery;
import com.yunpei.proxy.service.role.RoleManageModels.RoleMenuBindResult;
import com.yunpei.proxy.service.role.RoleManageModels.RoleView;
import com.yunpei.proxy.service.role.RoleManageModels.UpdateRoleCommand;
import com.yunpei.proxy.service.role.SysRoleService;
import io.github.linpeilie.Converter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 角色管理 Controller（路径对齐前端 {@code /api/system/role/*}）。
 *
 * <p>登录校验由 {@code WebConfig} 的 SaInterceptor 统一完成，本类不再重复 requireLogin。
 *
 * @author yunpei
 */
@Tag(name = "角色管理", description = "分页/CRUD/软删/菜单与 API 绑定")
@RestController
@RequestMapping("/api/system/role")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class RoleController {

    private final SysRoleService sysRoleService;
    private final Converter converter;

    @GetMapping("/list")
    @Operation(summary = "分页查询角色", description = "data={items,total}；附 userCount/parentName")
    public Result<PageData<RoleListItemVO>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status) {
        PageData<RoleView> pageData = sysRoleService.pageRoles(RoleListQuery.of(page, pageSize, code, name, status));
        List<RoleListItemVO> items = converter.convert(pageData.getItems(), RoleListItemVO.class);
        return Result.ok(PageData.of(items, pageData.getTotal()));
    }

    @GetMapping("/all")
    @Operation(summary = "全量角色", description = "用户表单角色下拉；可选 status 过滤")
    public Result<List<RoleListItemVO>> all(@RequestParam(required = false) Integer status) {
        List<RoleListItemVO> items = converter.convert(sysRoleService.listAll(status), RoleListItemVO.class);
        return Result.ok(items);
    }

    @PostMapping
    @Operation(summary = "创建角色")
    public Result<RoleListItemVO> create(@Valid @RequestBody CreateRoleRequest req) {
        CreateRoleCommand cmd = converter.convert(req, CreateRoleCommand.class);
        RoleView view = sysRoleService.create(cmd);
        return Result.ok(converter.convert(view, RoleListItemVO.class));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新角色", description = "code 不可改；parentId 省略不改，显式 null 清父")
    public Result<RoleListItemVO> update(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest req) {
        // ParentIdChange 含 presence 语义，保留手写组装
        ParentIdChange parentChange =
                req.isParentIdPresent() ? ParentIdChange.of(req.getParentId()) : ParentIdChange.absent();
        RoleView view = sysRoleService.update(new UpdateRoleCommand(
                id, req.getName(), parentChange, req.getSort(), req.getIsEnabled(), req.getRemark()));
        return Result.ok(converter.convert(view, RoleListItemVO.class));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "软删角色", description = "Root/有用户/有子角色时拒绝")
    public Result<RoleListItemVO> delete(@PathVariable Long id) {
        return Result.ok(converter.convert(sysRoleService.softDelete(id), RoleListItemVO.class));
    }

    @GetMapping("/{id}/menus")
    @Operation(summary = "角色菜单绑定列表", description = "全量菜单 + bound 标记")
    public Result<List<RoleMenuBindItemVO>> menus(@PathVariable Long id) {
        List<RoleMenuBindItemVO> items = converter.convert(sysRoleService.listMenuBinds(id), RoleMenuBindItemVO.class);
        return Result.ok(items);
    }

    @PostMapping("/{id}/menus")
    @Operation(summary = "全量替换角色菜单绑定")
    public Result<RoleMenuBindResultVO> setMenus(@PathVariable Long id, @Valid @RequestBody RoleMenuBindRequest req) {
        RoleMenuBindResult result = sysRoleService.replaceMenus(id, req.getMenuIds());
        return Result.ok(converter.convert(result, RoleMenuBindResultVO.class));
    }

    @GetMapping("/{id}/apis")
    @Operation(summary = "角色 API 绑定列表", description = "全量接口 + bound 标记")
    public Result<List<RoleApiBindItemVO>> apis(@PathVariable Long id) {
        List<RoleApiBindItemVO> items = converter.convert(sysRoleService.listApiBinds(id), RoleApiBindItemVO.class);
        return Result.ok(items);
    }

    @PostMapping("/{id}/apis")
    @Operation(summary = "全量替换角色 API 绑定", description = "变更后同步受影响用户的 Casbin 策略")
    public Result<RoleApiBindResultVO> setApis(@PathVariable Long id, @Valid @RequestBody RoleApiBindRequest req) {
        RoleApiBindResult result = sysRoleService.replaceApis(id, req.getApiIds());
        return Result.ok(converter.convert(result, RoleApiBindResultVO.class));
    }
}

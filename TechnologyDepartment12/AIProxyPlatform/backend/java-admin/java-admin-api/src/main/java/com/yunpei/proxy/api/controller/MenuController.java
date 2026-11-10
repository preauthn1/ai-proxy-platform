package com.yunpei.proxy.api.controller;

import com.yunpei.proxy.api.dto.ApisByMenusRequest;
import com.yunpei.proxy.api.dto.CreateMenuRequest;
import com.yunpei.proxy.api.dto.MenuApiBindRequest;
import com.yunpei.proxy.api.dto.MenuBatchRequest;
import com.yunpei.proxy.api.dto.UpdateMenuRequest;
import com.yunpei.proxy.api.vo.ApisByMenusResultVO;
import com.yunpei.proxy.api.vo.MenuApiBindItemVO;
import com.yunpei.proxy.api.vo.MenuApiBindResultVO;
import com.yunpei.proxy.api.vo.MenuBatchResultVO;
import com.yunpei.proxy.api.vo.MenuListItemVO;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.common.result.TreePageData;
import com.yunpei.proxy.service.menu.MenuManageModels.CreateMenuCommand;
import com.yunpei.proxy.service.menu.MenuManageModels.MenuApiBindResult;
import com.yunpei.proxy.service.menu.MenuManageModels.MenuBatchCommand;
import com.yunpei.proxy.service.menu.MenuManageModels.MenuBatchResult;
import com.yunpei.proxy.service.menu.MenuManageModels.MenuListPage;
import com.yunpei.proxy.service.menu.MenuManageModels.MenuListQuery;
import com.yunpei.proxy.service.menu.MenuManageModels.MenuView;
import com.yunpei.proxy.service.menu.MenuManageModels.MetadataChange;
import com.yunpei.proxy.service.menu.MenuManageModels.ParentIdChange;
import com.yunpei.proxy.service.menu.MenuManageModels.UpdateMenuCommand;
import com.yunpei.proxy.service.menu.SysMenuService;
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
 * 菜单管理 Controller（路径对齐前端 {@code /api/system/menu/*}）。
 *
 * <p>登录校验由 {@code WebConfig} 的 SaInterceptor 统一完成，本类不再重复 requireLogin。
 *
 * @author yunpei
 */
@Tag(name = "菜单管理", description = "树形 CRUD/软删/batch/menu-api 绑定/name-path 校验")
@RestController
@RequestMapping("/api/system/menu")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MenuController {

    private final SysMenuService sysMenuService;
    private final Converter converter;

    @GetMapping("/list")
    @Operation(summary = "分页查询菜单", description = "按根节点分页；data={items,total,itemTotal}")
    public Result<TreePageData<MenuListItemVO>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String permissionCode,
            @RequestParam(required = false) Integer status) {
        MenuListPage pageData =
                sysMenuService.pageMenus(MenuListQuery.of(page, pageSize, name, type, permissionCode, status));
        List<MenuListItemVO> items = converter.convert(pageData.items(), MenuListItemVO.class);
        return Result.ok(TreePageData.of(items, pageData.total(), pageData.itemTotal()));
    }

    @GetMapping("/all")
    @Operation(summary = "全量菜单", description = "供父菜单下拉与前端组树")
    public Result<List<MenuListItemVO>> all(
            @RequestParam(required = false) String type, @RequestParam(required = false) Integer status) {
        String typeFilter = type == null || "全部".equals(type) ? null : type;
        List<MenuListItemVO> items =
                converter.convert(sysMenuService.listAll(typeFilter, status), MenuListItemVO.class);
        return Result.ok(items);
    }

    @PostMapping
    @Operation(summary = "创建菜单")
    public Result<MenuListItemVO> create(@Valid @RequestBody CreateMenuRequest req) {
        CreateMenuCommand cmd = converter.convert(req, CreateMenuCommand.class);
        MenuView view = sysMenuService.create(cmd);
        return Result.ok(converter.convert(view, MenuListItemVO.class));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新菜单")
    public Result<MenuListItemVO> update(@PathVariable Long id, @RequestBody(required = false) UpdateMenuRequest body) {
        UpdateMenuRequest req = body == null ? new UpdateMenuRequest() : body;
        UpdateMenuCommand cmd = new UpdateMenuCommand(
                id,
                req.isParentIdPresent() ? ParentIdChange.of(req.getParentId()) : ParentIdChange.absent(),
                req.getName(),
                req.getType(),
                req.getPath(),
                req.getComponent(),
                req.getIcon(),
                req.getRedirect(),
                req.getPermissionCode(),
                req.isMetadataPresent() ? MetadataChange.of(req.getMetadata()) : MetadataChange.absent(),
                req.getSort(),
                req.getIsHidden(),
                req.getIsEnabled(),
                req.getRemark());
        return Result.ok(converter.convert(sysMenuService.update(cmd), MenuListItemVO.class));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "软删菜单")
    public Result<MenuListItemVO> delete(@PathVariable Long id) {
        return Result.ok(converter.convert(sysMenuService.softDelete(id), MenuListItemVO.class));
    }

    @PostMapping("/batch")
    @Operation(summary = "批量 enable|disable|delete")
    public Result<MenuBatchResultVO> batch(@RequestBody MenuBatchRequest req) {
        MenuBatchResult result = sysMenuService.batch(converter.convert(req, MenuBatchCommand.class));
        return Result.ok(converter.convert(result, MenuBatchResultVO.class));
    }

    @GetMapping("/{id}/apis")
    @Operation(summary = "读取菜单已绑定 API（含 bound 标记）")
    public Result<List<MenuApiBindItemVO>> menuApis(@PathVariable Long id) {
        List<MenuApiBindItemVO> items = converter.convert(sysMenuService.listMenuApis(id), MenuApiBindItemVO.class);
        return Result.ok(items);
    }

    @PostMapping("/{id}/apis")
    @Operation(summary = "全量替换菜单-API 绑定")
    public Result<MenuApiBindResultVO> setMenuApis(@PathVariable Long id, @RequestBody MenuApiBindRequest req) {
        MenuApiBindResult result = sysMenuService.setMenuApis(id, req.getApiIds());
        return Result.ok(converter.convert(result, MenuApiBindResultVO.class));
    }

    @PostMapping("/apis-by-menus")
    @Operation(summary = "按菜单聚合 API IDs")
    public Result<ApisByMenusResultVO> apisByMenus(@RequestBody ApisByMenusRequest req) {
        var result = sysMenuService.apisByMenus(req.getMenuIds());
        return Result.ok(converter.convert(result, ApisByMenusResultVO.class));
    }

    @GetMapping("/name-exists")
    @Operation(summary = "菜单名是否已存在", description = "true=冲突")
    public Result<Boolean> nameExists(
            @RequestParam(required = false) String name, @RequestParam(required = false) Long id) {
        return Result.ok(sysMenuService.nameExists(name, id));
    }

    @GetMapping("/path-exists")
    @Operation(summary = "路由 path 是否已存在", description = "true=冲突")
    public Result<Boolean> pathExists(
            @RequestParam(required = false) String path, @RequestParam(required = false) Long id) {
        return Result.ok(sysMenuService.pathExists(path, id));
    }
}

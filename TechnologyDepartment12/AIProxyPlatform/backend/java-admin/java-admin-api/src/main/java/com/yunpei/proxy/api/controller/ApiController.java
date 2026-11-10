package com.yunpei.proxy.api.controller;

import com.yunpei.proxy.api.dto.ApiBatchRequest;
import com.yunpei.proxy.api.dto.CreateApiRequest;
import com.yunpei.proxy.api.dto.UpdateApiRequest;
import com.yunpei.proxy.api.vo.ApiBatchResultVO;
import com.yunpei.proxy.api.vo.ApiListItemVO;
import com.yunpei.proxy.api.vo.ApiSyncResultVO;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.common.result.TreePageData;
import com.yunpei.proxy.service.api.ApiManageModels.ApiBatchCommand;
import com.yunpei.proxy.service.api.ApiManageModels.ApiBatchResult;
import com.yunpei.proxy.service.api.ApiManageModels.ApiListPage;
import com.yunpei.proxy.service.api.ApiManageModels.ApiListQuery;
import com.yunpei.proxy.service.api.ApiManageModels.ApiSyncResult;
import com.yunpei.proxy.service.api.ApiManageModels.ApiView;
import com.yunpei.proxy.service.api.ApiManageModels.CreateApiCommand;
import com.yunpei.proxy.service.api.ApiManageModels.UpdateApiCommand;
import com.yunpei.proxy.service.api.SysApiService;
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
 * API 资源管理 Controller（路径对齐前端 {@code /api/system/api/*}）。
 *
 * <p>登录校验由 {@code WebConfig} 的 SaInterceptor 统一完成，本类不再重复 requireLogin。
 *
 * @author yunpei
 */
@Tag(name = "API 资源管理", description = "按组分页/CRUD/软删/groups/all/batch/sync")
@RestController
@RequestMapping("/api/system/api")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ApiController {

    private final SysApiService sysApiService;
    private final Converter converter;

    @GetMapping("/list")
    @Operation(summary = "分页查询 API", description = "分页基数为分组；data={items,total,itemTotal}")
    public Result<TreePageData<ApiListItemVO>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String group,
            @RequestParam(required = false) Integer status) {
        ApiListPage pageData =
                sysApiService.pageApis(ApiListQuery.of(page, pageSize, name, path, method, group, status));
        List<ApiListItemVO> items = converter.convert(pageData.items(), ApiListItemVO.class);
        return Result.ok(TreePageData.of(items, pageData.total(), pageData.itemTotal()));
    }

    @GetMapping("/all")
    @Operation(summary = "全量 API", description = "未软删；供角色/菜单绑定联动")
    public Result<List<ApiListItemVO>> all() {
        List<ApiListItemVO> items = converter.convert(sysApiService.listAll(), ApiListItemVO.class);
        return Result.ok(items);
    }

    @GetMapping("/groups")
    @Operation(summary = "去重分组列表", description = "供分组下拉")
    public Result<List<String>> groups() {
        return Result.ok(sysApiService.listGroups());
    }

    @PostMapping
    @Operation(summary = "创建 API")
    public Result<ApiListItemVO> create(@Valid @RequestBody CreateApiRequest req) {
        CreateApiCommand cmd = converter.convert(req, CreateApiCommand.class);
        ApiView view = sysApiService.create(cmd);
        return Result.ok(converter.convert(view, ApiListItemVO.class));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新 API")
    public Result<ApiListItemVO> update(@PathVariable Long id, @Valid @RequestBody UpdateApiRequest req) {
        // id 来自路径，映射体只覆盖可改字段
        UpdateApiCommand body = converter.convert(req, UpdateApiCommand.class);
        UpdateApiCommand cmd = new UpdateApiCommand(
                id,
                body.name(),
                body.method(),
                body.path(),
                body.permissionCode(),
                body.apiGroup(),
                body.remark(),
                body.isEnabled());
        return Result.ok(converter.convert(sysApiService.update(cmd), ApiListItemVO.class));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "软删 API", description = "清 menu_api/role_api 后软删")
    public Result<ApiListItemVO> delete(@PathVariable Long id) {
        return Result.ok(converter.convert(sysApiService.softDelete(id), ApiListItemVO.class));
    }

    @PostMapping("/batch")
    @Operation(summary = "批量 enable|disable|delete")
    public Result<ApiBatchResultVO> batch(@RequestBody ApiBatchRequest req) {
        ApiBatchResult result = sysApiService.batch(converter.convert(req, ApiBatchCommand.class));
        return Result.ok(converter.convert(result, ApiBatchResultVO.class));
    }

    @PostMapping("/sync")
    @Operation(summary = "同步路由清单", description = "按内置 manifest upsert；命中 method+path 则 skip")
    public Result<ApiSyncResultVO> sync() {
        ApiSyncResult result = sysApiService.syncFromManifest();
        return Result.ok(converter.convert(result, ApiSyncResultVO.class));
    }
}

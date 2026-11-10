package com.yunpei.proxy.api.controller;

import com.yunpei.proxy.api.dto.BlacklistBatchRequest;
import com.yunpei.proxy.api.dto.CreateBlacklistRequest;
import com.yunpei.proxy.api.dto.UpdateBlacklistRequest;
import com.yunpei.proxy.api.vo.BlacklistBatchResultVO;
import com.yunpei.proxy.api.vo.BlacklistVO;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.service.blacklist.BlacklistManageModels.BlacklistBatchCommand;
import com.yunpei.proxy.service.blacklist.BlacklistManageModels.BlacklistBatchResult;
import com.yunpei.proxy.service.blacklist.BlacklistManageModels.BlacklistListQuery;
import com.yunpei.proxy.service.blacklist.BlacklistManageModels.BlacklistView;
import com.yunpei.proxy.service.blacklist.BlacklistManageModels.CreateBlacklistCommand;
import com.yunpei.proxy.service.blacklist.BlacklistManageModels.UpdateBlacklistCommand;
import com.yunpei.proxy.service.blacklist.BlacklistService;
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
 * 访问黑名单管理（路径 {@code /api/system/blacklist/*}）。
 *
 * @author yunpei
 */
@Tag(name = "访问黑名单", description = "分页/all/CRUD/软删/batch")
@RestController
@RequestMapping("/api/system/blacklist")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class BlacklistController {

    private final BlacklistService blacklistService;
    private final Converter converter;

    @GetMapping("/list")
    @Operation(summary = "分页查询黑名单", description = "data={items,total}；可按 targetType/targetValue/scope/status 筛选")
    public Result<PageData<BlacklistVO>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetValue,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) Integer status) {
        PageData<BlacklistView> pageData =
                blacklistService.page(BlacklistListQuery.of(page, pageSize, targetType, targetValue, scope, status));
        List<BlacklistVO> items = converter.convert(pageData.getItems(), BlacklistVO.class);
        return Result.ok(PageData.of(items, pageData.getTotal()));
    }

    @GetMapping("/all")
    @Operation(summary = "全量黑名单", description = "支持与 list 相同过滤项")
    public Result<List<BlacklistVO>> all(
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetValue,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) Integer status) {
        List<BlacklistVO> items = converter.convert(
                blacklistService.listAll(BlacklistListQuery.allFilter(targetType, targetValue, scope, status)),
                BlacklistVO.class);
        return Result.ok(items);
    }

    @GetMapping("/{id}")
    @Operation(summary = "黑名单详情")
    public Result<BlacklistVO> detail(@PathVariable Long id) {
        return Result.ok(converter.convert(blacklistService.getById(id), BlacklistVO.class));
    }

    @PostMapping
    @Operation(summary = "创建黑名单")
    public Result<BlacklistVO> create(@Valid @RequestBody CreateBlacklistRequest req) {
        CreateBlacklistCommand cmd = converter.convert(req, CreateBlacklistCommand.class);
        return Result.ok(converter.convert(blacklistService.create(cmd), BlacklistVO.class));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新黑名单")
    public Result<BlacklistVO> update(@PathVariable Long id, @Valid @RequestBody UpdateBlacklistRequest req) {
        UpdateBlacklistCommand body = converter.convert(req, UpdateBlacklistCommand.class);
        UpdateBlacklistCommand cmd = new UpdateBlacklistCommand(
                id,
                body.targetType(),
                body.targetValue(),
                body.scope(),
                body.reason(),
                body.startsAt(),
                body.expiresAt(),
                body.clearExpiresAt(),
                body.remark(),
                body.isEnabled());
        return Result.ok(converter.convert(blacklistService.update(cmd), BlacklistVO.class));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "软删黑名单")
    public Result<BlacklistVO> delete(@PathVariable Long id) {
        return Result.ok(converter.convert(blacklistService.softDelete(id), BlacklistVO.class));
    }

    @PostMapping("/batch")
    @Operation(summary = "批量 enable|disable|delete")
    public Result<BlacklistBatchResultVO> batch(@RequestBody BlacklistBatchRequest req) {
        BlacklistBatchResult result = blacklistService.batch(converter.convert(req, BlacklistBatchCommand.class));
        return Result.ok(converter.convert(result, BlacklistBatchResultVO.class));
    }
}

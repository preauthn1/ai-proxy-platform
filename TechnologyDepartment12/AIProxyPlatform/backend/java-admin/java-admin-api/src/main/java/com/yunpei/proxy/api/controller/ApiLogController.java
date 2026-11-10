package com.yunpei.proxy.api.controller;

import com.yunpei.proxy.api.vo.ApiLogVO;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.service.log.ApiLogService;
import com.yunpei.proxy.service.log.LogManageModels.ApiLogListQuery;
import com.yunpei.proxy.service.log.LogManageModels.ApiLogView;
import io.github.linpeilie.Converter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 调用日志（路径对齐前端 {@code /api/system/api-log/*}）。
 *
 * @author yunpei
 */
@Tag(name = "API 日志", description = "分页查询热表/归档")
@RestController
@RequestMapping("/api/system/api-log")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ApiLogController {

    private final ApiLogService apiLogService;
    private final Converter converter;

    @GetMapping("/list")
    @Operation(
            summary = "分页查询 API 调用日志",
            description =
                    "data={items,total}；source=hot|archive；筛选 method/module/path/success/statusCode/username/clientIp/requestId/createdAt")
    public Result<PageData<ApiLogVO>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) Integer success,
            @RequestParam(required = false) Integer statusCode,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String clientIp,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String createdAtFrom,
            @RequestParam(required = false) String createdAtTo) {
        PageData<ApiLogView> pageData = apiLogService.page(ApiLogListQuery.of(
                page,
                pageSize,
                source,
                method,
                module,
                path,
                success,
                statusCode,
                username,
                clientIp,
                requestId,
                createdAtFrom,
                createdAtTo));
        List<ApiLogVO> items = converter.convert(pageData.getItems(), ApiLogVO.class);
        return Result.ok(PageData.of(items, pageData.getTotal()));
    }
}

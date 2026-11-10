package com.yunpei.proxy.api.controller;

import com.yunpei.proxy.api.vo.LoginLogVO;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.service.log.LogManageModels.LoginLogListQuery;
import com.yunpei.proxy.service.log.LogManageModels.LoginLogView;
import com.yunpei.proxy.service.log.LoginLogService;
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
 * 登录日志（路径对齐前端 {@code /api/system/login-log/*}）。
 *
 * @author yunpei
 */
@Tag(name = "登录日志", description = "分页查询热表/归档")
@RestController
@RequestMapping("/api/system/login-log")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class LoginLogController {

    private final LoginLogService loginLogService;
    private final Converter converter;

    @GetMapping("/list")
    @Operation(
            summary = "分页查询登录日志",
            description = "data={items,total}；source=hot|archive；筛选 username/success/loginMethod/loginIp/loginTime")
    public Result<PageData<LoginLogVO>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer success,
            @RequestParam(required = false) String loginMethod,
            @RequestParam(required = false) String loginIp,
            @RequestParam(required = false) String loginTimeFrom,
            @RequestParam(required = false) String loginTimeTo) {
        PageData<LoginLogView> pageData = loginLogService.page(LoginLogListQuery.of(
                page, pageSize, source, username, success, loginMethod, loginIp, loginTimeFrom, loginTimeTo));
        List<LoginLogVO> items = converter.convert(pageData.getItems(), LoginLogVO.class);
        return Result.ok(PageData.of(items, pageData.getTotal()));
    }
}

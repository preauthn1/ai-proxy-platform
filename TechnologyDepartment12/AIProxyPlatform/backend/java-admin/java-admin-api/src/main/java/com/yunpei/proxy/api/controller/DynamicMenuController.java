package com.yunpei.proxy.api.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.yunpei.proxy.api.vo.RuntimeMenuRouteVO;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.service.menu.SysMenuService;
import io.github.linpeilie.Converter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录用户动态菜单路由（{@code GET /api/menu/all}）。
 *
 * <p>登录校验由 {@code WebConfig} SaInterceptor 统一完成；本类只读取 loginId。
 *
 * @author yunpei
 */
@Tag(name = "动态菜单", description = "按当前用户角色过滤的侧栏路由树")
@RestController
@RequestMapping("/api/menu")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class DynamicMenuController {

    private final SysMenuService sysMenuService;
    private final Converter converter;

    @GetMapping("/all")
    @Operation(summary = "当前用户动态菜单", description = "user→role_menu→祖先补全→DIR/MENU 投影")
    public Result<List<RuntimeMenuRouteVO>> all() {
        Long userId = StpUtil.getLoginIdAsLong();
        List<RuntimeMenuRouteVO> routes =
                converter.convert(sysMenuService.listRuntimeMenusForUser(userId), RuntimeMenuRouteVO.class);
        return Result.ok(routes);
    }
}

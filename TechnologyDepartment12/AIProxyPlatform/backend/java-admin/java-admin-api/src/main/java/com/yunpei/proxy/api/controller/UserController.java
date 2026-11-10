package com.yunpei.proxy.api.controller;

import com.yunpei.proxy.api.dto.CreateUserRequest;
import com.yunpei.proxy.api.dto.ResetPasswordRequest;
import com.yunpei.proxy.api.dto.ToggleUserStatusRequest;
import com.yunpei.proxy.api.dto.UpdateUserRequest;
import com.yunpei.proxy.api.vo.IdOnlyVO;
import com.yunpei.proxy.api.vo.UserListItemVO;
import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.service.user.SysUserService;
import com.yunpei.proxy.service.user.UserManageModels.CreateUserCommand;
import com.yunpei.proxy.service.user.UserManageModels.UpdateUserCommand;
import com.yunpei.proxy.service.user.UserManageModels.UserListQuery;
import com.yunpei.proxy.service.user.UserManageModels.UserView;
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
 * 用户管理 Controller（路径对齐前端 {@code /api/system/user/*}）。
 *
 * <p>登录校验由 {@code WebConfig} 的 SaInterceptor 统一完成，本类不再重复 requireLogin。
 *
 * @author yunpei
 */
@Tag(name = "用户管理", description = "分页/CRUD/软删/启停/重置密码/角色分配")
@RestController
@RequestMapping("/api/system/user")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final SysUserService sysUserService;
    private final Converter converter;

    @GetMapping("/list")
    @Operation(summary = "分页查询用户", description = "data={items,total}；筛选 username/nickname/status/roleId")
    public Result<PageData<UserListItemVO>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String nickname,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long roleId) {
        PageData<UserView> pageData =
                sysUserService.pageUsers(UserListQuery.of(page, pageSize, username, nickname, status, roleId));
        List<UserListItemVO> items = converter.convert(pageData.getItems(), UserListItemVO.class);
        return Result.ok(PageData.of(items, pageData.getTotal()));
    }

    @PostMapping
    @Operation(summary = "创建用户")
    public Result<UserListItemVO> create(@Valid @RequestBody CreateUserRequest req) {
        CreateUserCommand cmd = converter.convert(req, CreateUserCommand.class);
        UserView view = sysUserService.create(cmd);
        return Result.ok(converter.convert(view, UserListItemVO.class));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新用户", description = "username/password 不可改；roleIds 省略不改角色")
    public Result<UserListItemVO> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest req) {
        // roleIds：JSON 显式 null 与省略在 Jackson 下均为 null → 不改；传 [] 清空
        // id 来自路径，映射体只覆盖可改字段
        UpdateUserCommand body = converter.convert(req, UpdateUserCommand.class);
        UpdateUserCommand cmd = new UpdateUserCommand(
                id,
                body.nickname(),
                body.email(),
                body.phone(),
                body.avatar(),
                body.languageCode(),
                body.isEnabled(),
                body.remark(),
                body.roleIds());
        UserView view = sysUserService.update(cmd);
        return Result.ok(converter.convert(view, UserListItemVO.class));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "软删用户")
    public Result<UserListItemVO> delete(@PathVariable Long id) {
        return Result.ok(converter.convert(sysUserService.softDelete(id), UserListItemVO.class));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "启停用户")
    public Result<UserListItemVO> toggleStatus(@PathVariable Long id, @RequestBody ToggleUserStatusRequest req) {
        Integer status = req.getStatus() != null ? req.getStatus() : req.getIsEnabled();
        if (status == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "status 必须为 0 或 1");
        }
        return Result.ok(converter.convert(sysUserService.toggleStatus(id, status), UserListItemVO.class));
    }

    @PostMapping("/{id}/password")
    @Operation(summary = "重置密码", description = "BCrypt 存储；响应仅 {id}")
    public Result<IdOnlyVO> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest req) {
        Long userId = sysUserService.resetPassword(id, req.getPassword());
        return Result.ok(new IdOnlyVO(userId));
    }
}

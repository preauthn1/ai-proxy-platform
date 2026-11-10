package com.yunpei.proxy.api.vo;

import com.yunpei.proxy.service.user.UserManageModels.UserView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户列表/详情 VO（无 passwordHash）。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = UserView.class)
@Schema(description = "用户信息（管理端）")
public class UserListItemVO {

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String avatar;
    private String languageCode;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private String remark;
    private Integer isEnabled;
    private Long deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<Long> roleIds;
    private List<String> roleNames;
}

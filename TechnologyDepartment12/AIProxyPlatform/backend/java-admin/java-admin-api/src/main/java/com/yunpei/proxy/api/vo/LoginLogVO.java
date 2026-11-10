package com.yunpei.proxy.api.vo;

import com.yunpei.proxy.service.log.LogManageModels.LoginLogView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录日志列表项 VO（对齐 mock camelCase 输出）。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = LoginLogView.class)
@Schema(description = "登录日志")
public class LoginLogVO {

    private Long id;
    private String username;
    private Integer success;
    private String reason;
    private Integer statusCode;
    private Long sysUserId;
    private String loginMethod;
    private LocalDateTime loginTime;
    private String loginIp;
    private String loginMac;
    private String clientId;
    private String clientName;
    private String userAgent;
    private String browserName;
    private String browserVersion;
    private String osName;
    private String osVersion;
    private String location;
    private LocalDateTime createdAt;
    /** 仅 source=archive 时有值 */
    private LocalDateTime archivedAt;
}

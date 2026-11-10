package com.yunpei.proxy.api.vo;

import com.yunpei.proxy.service.log.LogManageModels.ApiLogView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API 调用日志列表项 VO（对齐 mock camelCase 输出）。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = ApiLogView.class)
@Schema(description = "API 调用日志")
public class ApiLogVO {

    private Long id;
    private String method;
    private String module;
    private String path;
    private Integer statusCode;
    private Integer success;
    private String reason;
    private Long costTime;
    private String requestId;
    private Long sysUserId;
    private String username;
    private String requestUri;
    private String requestQuery;
    private String requestBody;
    private String requestHeader;
    private String referer;
    private String response;
    private String beforeChange;
    private String afterChange;
    private String formatChange;
    private String clientId;
    private String clientName;
    private String clientIp;
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

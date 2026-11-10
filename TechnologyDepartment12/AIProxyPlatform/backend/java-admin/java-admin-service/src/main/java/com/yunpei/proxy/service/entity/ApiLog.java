package com.yunpei.proxy.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.yunpei.proxy.service.entity.proxy.ApiLogProxy;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * API 调用日志实体（只增不改；对齐 schema v10 {@code api_log}）。
 *
 * <p>非核心业务表：无软删 / 无 updated_at / 无 created_by，故不继承 {@link BaseEntity}。
 *
 * @author yunpei
 */
@Data
@Table("api_log")
@EntityProxy
public class ApiLog implements ProxyEntityAvailable<ApiLog, ApiLogProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    private String method;

    private String module;

    private String path;

    private Integer statusCode;

    /** 1=成功 0=失败（按 HTTP status 2xx 判定） */
    private Integer success;

    private String reason;

    /** 耗时毫秒 */
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
}

package com.yunpei.proxy.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.yunpei.proxy.service.entity.proxy.ApiLogArchiveProxy;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * API 调用日志归档实体（对齐 schema v10 {@code api_log_archive}）。
 *
 * @author yunpei
 */
@Data
@Table("api_log_archive")
@EntityProxy
public class ApiLogArchive implements ProxyEntityAvailable<ApiLogArchive, ApiLogArchiveProxy> {

    @Column(primaryKey = true, generatedKey = true)
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

    private LocalDateTime archivedAt;
}

package com.yunpei.proxy.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.yunpei.proxy.service.entity.proxy.TemporalTaskConfigProxy;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Temporal 任务配置实体（对齐 schema {@code temporal_task_config}）。
 *
 * <p>{@code retryPolicy} 以 JSON 字符串落库，读写由业务层序列化。
 *
 * @author yunpei
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("temporal_task_config")
@EntityProxy
public class TemporalTaskConfig extends BaseEntity
        implements ProxyEntityAvailable<TemporalTaskConfig, TemporalTaskConfigProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** 任务编码（如 report_daily）。 */
    private String code;

    /** 展示名。 */
    private String name;

    /** Temporal workflow 类名。 */
    private String workflowType;

    /** Temporal task queue。 */
    private String taskQueue;

    /** cron 表达式；null = 仅手动触发。 */
    private String cronExpr;

    /** 重试策略 JSON 文本。 */
    private String retryPolicy;

    /** 超时秒数。 */
    private Integer timeoutSeconds;

    private String remark;

    /** 1=启用 0=禁用。 */
    private Integer isEnabled;
}

package com.yunpei.proxy.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.yunpei.proxy.service.entity.proxy.TemporalTaskExecutionProxy;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Temporal 执行记录镜像（对齐 schema {@code temporal_task_execution}）。
 *
 * <p>手动触发：start 业务 WF 后立即 insert 种子行（通常 {@code PENDING} + 真实 workflowId/runId，
 * {@code pendingAt} 有值、{@code startedAt} 为空）；Schedule 触发：由镜像 tick 从 Visibility
 * 发现后 upsert。状态/结果由 {@code ExecutionMirrorTick} 定时对账推进；无软删、无
 * {@code updated_at}，故不继承 {@link BaseEntity}。
 *
 * @author yunpei
 */
@Data
@Table("temporal_task_execution")
@EntityProxy
public class TemporalTaskExecution implements ProxyEntityAvailable<TemporalTaskExecution, TemporalTaskExecutionProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** 软外键 → temporal_task_config.id；配置软删后可悬空。 */
    private Long configId;

    private String workflowId;

    private String runId;

    private String workflowType;

    private String taskQueue;

    /**
     * PENDING / RUNNING / RETRYING / COMPLETED / FAILED / CANCELLED / TERMINATED / TIMED_OUT /
     * CONTINUED_AS_NEW。
     */
    private String status;

    /** 进入等待中（PENDING）的时间；首次进入等待时写入，之后不覆盖。 */
    private LocalDateTime pendingAt;

    /** 真正运行开始时间；尚未真正运行（含排队 PENDING）时为 null；首次非 PENDING 时写入。 */
    private LocalDateTime startedAt;

    /** 关闭时间；null=仍在运行或尚未启动。 */
    private LocalDateTime closedAt;

    /** 输入摘要 JSON。 */
    private String inputSummary;

    /** 结果摘要 JSON。 */
    private String resultSummary;

    /** 失败原因全文；无失败时为 null。 */
    private String failureReason;

    /**
     * 已发生重试次数；首次执行为 0（非剩余次数）。写路径尚未镜像 Temporal 自动 retry 时恒为 0。
     */
    private Integer retryCount;

    private LocalDateTime createdAt;
}

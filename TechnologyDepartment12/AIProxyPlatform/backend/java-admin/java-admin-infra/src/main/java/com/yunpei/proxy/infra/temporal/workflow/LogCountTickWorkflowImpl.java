package com.yunpei.proxy.infra.temporal.workflow;

import com.yunpei.proxy.service.task.TemporalTaskQueue;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.util.Map;

/**
 * {@link LogCountTickWorkflow} 实现：单次 tick（Activity 计数+1 并打日志后结束）。
 *
 * <p>节拍由 Temporal Schedule（DB {@code cron_expr} / interval）驱动，不再在 Workflow 内
 * {@code sleep} 死循环，避免与 Schedule 叠多实例或固定 workflowId 挡住后续调度。
 * Worker 注册队列见 {@link TemporalTaskQueue#DEMO}（业务 / 联调队列）。
 *
 * <p>Activity 显式重试策略（示例）：有限次数 + 短间隔，便于镜像 tick 观察到
 * {@code RETRYING}/{@code retry_count}（见 {@link ExecutionMirrorTickActivitiesImpl}）。
 *
 * @author yunpei
 */
@WorkflowImpl(taskQueues = TemporalTaskQueue.DEMO)
public class LogCountTickWorkflowImpl implements LogCountTickWorkflow {

    private final LogCountTickActivities activities = Workflow.newActivityStub(
            LogCountTickActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(5)
                            .setInitialInterval(Duration.ofSeconds(2))
                            .setBackoffCoefficient(1.5)
                            .setMaximumInterval(Duration.ofSeconds(10))
                            .build())
                    .build());

    @Override
    public Map<String, Object> run(Map<String, Object> input) {
        Map<String, Object> result = activities.incrementAndLog(input);
        Object count = result == null ? null : result.get("count");
        Workflow.getLogger(LogCountTickWorkflowImpl.class)
                .info("LogCountTick done count={} inputKeys={}", count, input == null ? 0 : input.size());
        return result;
    }
}

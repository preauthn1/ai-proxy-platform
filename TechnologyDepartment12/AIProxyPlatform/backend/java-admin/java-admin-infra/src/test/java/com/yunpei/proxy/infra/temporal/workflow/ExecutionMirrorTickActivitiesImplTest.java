package com.yunpei.proxy.infra.temporal.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunpei.proxy.infra.temporal.workflow.ExecutionMirrorTickActivitiesImpl.RetrySnapshot;
import com.yunpei.proxy.service.entity.TemporalTaskExecution;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.common.v1.WorkflowType;
import io.temporal.api.enums.v1.PendingActivityState;
import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.api.failure.v1.Failure;
import io.temporal.api.workflow.v1.WorkflowExecutionInfo;
import io.temporal.client.WorkflowExecutionMetadata;
import io.temporal.common.converter.DataConverter;
import io.temporal.common.converter.GlobalDataConverter;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link ExecutionMirrorTickActivitiesImpl} 纯函数映射单测。
 */
class ExecutionMirrorTickActivitiesImplTest {

    @Test
    void mapStatus_coversTerminalAndRunning() {
        assertThat(ExecutionMirrorTickActivitiesImpl.mapStatus(
                        WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_COMPLETED))
                .isEqualTo("COMPLETED");
        assertThat(ExecutionMirrorTickActivitiesImpl.mapStatus(
                        WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_FAILED))
                .isEqualTo("FAILED");
        assertThat(ExecutionMirrorTickActivitiesImpl.mapStatus(
                        WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_CANCELED))
                .isEqualTo("CANCELLED");
        assertThat(ExecutionMirrorTickActivitiesImpl.mapStatus(
                        WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_TIMED_OUT))
                .isEqualTo("TIMED_OUT");
        assertThat(ExecutionMirrorTickActivitiesImpl.mapStatus(
                        WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_RUNNING))
                .isEqualTo("RUNNING");
        assertThat(ExecutionMirrorTickActivitiesImpl.mapStatus(
                        WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_PAUSED))
                .isEqualTo("RUNNING");
    }

    @Test
    void resolveOpenStatus_promotesRunningToRetryingWhenActivityRetrying() {
        RetrySnapshot retrying = new RetrySnapshot(true, true, false, 2, "boom", null, null);
        assertThat(ExecutionMirrorTickActivitiesImpl.resolveOpenStatus("RUNNING", retrying))
                .isEqualTo("RETRYING");
        assertThat(ExecutionMirrorTickActivitiesImpl.resolveOpenStatus("FAILED", retrying))
                .isEqualTo("FAILED");
        assertThat(ExecutionMirrorTickActivitiesImpl.resolveOpenStatus("RUNNING", RetrySnapshot.notRetrying()))
                .isEqualTo("RUNNING");
        assertThat(ExecutionMirrorTickActivitiesImpl.resolveOpenStatus("RUNNING", RetrySnapshot.unknown()))
                .isEqualTo("RUNNING");
        assertThat(ExecutionMirrorTickActivitiesImpl.resolveOpenStatus("RUNNING", null))
                .isEqualTo("RUNNING");
    }

    @Test
    void resolveOpenStatus_promotesRunningToPendingWhenActivityWaiting() {
        RetrySnapshot waiting = new RetrySnapshot(true, false, true, 0, null, null, null);
        assertThat(ExecutionMirrorTickActivitiesImpl.resolveOpenStatus("RUNNING", waiting))
                .isEqualTo("PENDING");
        // 重试优先于等待
        RetrySnapshot retryWhileScheduled = new RetrySnapshot(true, true, true, 1, "boom", null, null);
        assertThat(ExecutionMirrorTickActivitiesImpl.resolveOpenStatus("RUNNING", retryWhileScheduled))
                .isEqualTo("RETRYING");
        // 已真正执行：保持 RUNNING
        RetrySnapshot started = new RetrySnapshot(true, false, false, 0, null, null, null);
        assertThat(ExecutionMirrorTickActivitiesImpl.resolveOpenStatus("RUNNING", started))
                .isEqualTo("RUNNING");
        // 终态不降级
        assertThat(ExecutionMirrorTickActivitiesImpl.resolveOpenStatus("COMPLETED", waiting))
                .isEqualTo("COMPLETED");
    }

    @Test
    void resolvePendingAt_and_startedAt_preferActivityTimes() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 20, 8, 0);
        LocalDateTime temporal = LocalDateTime.of(2026, 6, 20, 7, 59);
        LocalDateTime scheduled = LocalDateTime.of(2026, 6, 20, 7, 59, 30);
        LocalDateTime lastStarted = LocalDateTime.of(2026, 6, 20, 8, 0, 10);
        TemporalTaskExecution empty = new TemporalTaskExecution();
        RetrySnapshot unknown = RetrySnapshot.unknown();
        RetrySnapshot withTimes = new RetrySnapshot(true, false, false, 0, null, scheduled, lastStarted);

        // 无 Activity 时间 → workflow start 兜底；PENDING 时 startedAt 仍 null
        assertThat(ExecutionMirrorTickActivitiesImpl.resolvePendingAt(empty, unknown, temporal, now))
                .isEqualTo(temporal);
        assertThat(ExecutionMirrorTickActivitiesImpl.resolveStartedAt(empty, "PENDING", unknown, temporal, now))
                .isNull();

        // 有 scheduled_time / last_started_time → 分别写入，互不相等
        assertThat(ExecutionMirrorTickActivitiesImpl.resolvePendingAt(empty, withTimes, temporal, now))
                .isEqualTo(scheduled);
        assertThat(ExecutionMirrorTickActivitiesImpl.resolveStartedAt(empty, "RUNNING", withTimes, temporal, now))
                .isEqualTo(lastStarted);

        // 无 Activity 时间：pendingAt 回退 workflow start / now（保证非空）；startedAt 同逻辑
        assertThat(ExecutionMirrorTickActivitiesImpl.resolvePendingAt(empty, unknown, temporal, now))
                .isEqualTo(temporal);
        assertThat(ExecutionMirrorTickActivitiesImpl.resolvePendingAt(empty, unknown, null, now))
                .isEqualTo(now);
        assertThat(ExecutionMirrorTickActivitiesImpl.resolveStartedAt(empty, "RUNNING", unknown, temporal, now))
                .isEqualTo(temporal);
        assertThat(ExecutionMirrorTickActivitiesImpl.resolveStartedAt(empty, "RETRYING", unknown, null, now))
                .isEqualTo(now);

        // 库内已有值不覆盖
        TemporalTaskExecution seeded = new TemporalTaskExecution();
        seeded.setPendingAt(now.minusMinutes(1));
        seeded.setStartedAt(now.minusSeconds(30));
        assertThat(ExecutionMirrorTickActivitiesImpl.resolvePendingAt(seeded, withTimes, temporal, now))
                .isEqualTo(now.minusMinutes(1));
        assertThat(ExecutionMirrorTickActivitiesImpl.resolveStartedAt(seeded, "PENDING", withTimes, temporal, now))
                .isEqualTo(now.minusSeconds(30));
    }

    @Test
    void minTime_picksEarlierNonNull() {
        LocalDateTime a = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime b = LocalDateTime.of(2026, 1, 1, 0, 1);
        assertThat(ExecutionMirrorTickActivitiesImpl.minTime(null, b)).isEqualTo(b);
        assertThat(ExecutionMirrorTickActivitiesImpl.minTime(a, null)).isEqualTo(a);
        assertThat(ExecutionMirrorTickActivitiesImpl.minTime(a, b)).isEqualTo(a);
        assertThat(ExecutionMirrorTickActivitiesImpl.minTime(b, a)).isEqualTo(a);
    }

    @Test
    void isActivityStartedState_distinguishesScheduledFromStarted() {
        assertThat(ExecutionMirrorTickActivitiesImpl.isActivityStartedState(
                        PendingActivityState.PENDING_ACTIVITY_STATE_STARTED))
                .isTrue();
        assertThat(ExecutionMirrorTickActivitiesImpl.isActivityStartedState(
                        PendingActivityState.PENDING_ACTIVITY_STATE_CANCEL_REQUESTED))
                .isTrue();
        assertThat(ExecutionMirrorTickActivitiesImpl.isActivityStartedState(
                        PendingActivityState.PENDING_ACTIVITY_STATE_PAUSE_REQUESTED))
                .isTrue();
        assertThat(ExecutionMirrorTickActivitiesImpl.isActivityStartedState(
                        PendingActivityState.PENDING_ACTIVITY_STATE_SCHEDULED))
                .isFalse();
        assertThat(ExecutionMirrorTickActivitiesImpl.isActivityStartedState(
                        PendingActivityState.PENDING_ACTIVITY_STATE_PAUSED))
                .isFalse();
        assertThat(ExecutionMirrorTickActivitiesImpl.isActivityStartedState(
                        PendingActivityState.PENDING_ACTIVITY_STATE_UNSPECIFIED))
                .isFalse();
        assertThat(ExecutionMirrorTickActivitiesImpl.isActivityStartedState(null))
                .isFalse();
    }

    @Test
    void retryCountFromAttempt_firstIsZero() {
        assertThat(ExecutionMirrorTickActivitiesImpl.retryCountFromAttempt(0)).isZero();
        assertThat(ExecutionMirrorTickActivitiesImpl.retryCountFromAttempt(1)).isZero();
        assertThat(ExecutionMirrorTickActivitiesImpl.retryCountFromAttempt(2)).isEqualTo(1);
        assertThat(ExecutionMirrorTickActivitiesImpl.retryCountFromAttempt(5)).isEqualTo(4);
    }

    @Test
    void failureMessage_trimsAndNullSafe() {
        assertThat(ExecutionMirrorTickActivitiesImpl.failureMessage(null)).isNull();
        assertThat(ExecutionMirrorTickActivitiesImpl.failureMessage(Failure.getDefaultInstance()))
                .isNull();
        assertThat(ExecutionMirrorTickActivitiesImpl.failureMessage(
                        Failure.newBuilder().setMessage("  activity failed  ").build()))
                .isEqualTo("activity failed");
    }

    @Test
    void extractRetrySnapshot_visibilityMetadataIsUnknown() {
        WorkflowExecutionInfo info = WorkflowExecutionInfo.newBuilder()
                .setExecution(
                        WorkflowExecution.newBuilder().setWorkflowId("wf-1").setRunId("run-1"))
                .setType(WorkflowType.newBuilder().setName("LogCountTickWorkflow"))
                .setStatus(WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_RUNNING)
                .setTaskQueue("demo")
                .build();
        DataConverter converter = GlobalDataConverter.get();
        WorkflowExecutionMetadata meta = new WorkflowExecutionMetadata(info, converter);

        RetrySnapshot snapshot = ExecutionMirrorTickActivitiesImpl.extractRetrySnapshot(meta);
        assertThat(snapshot.known()).isFalse();
        assertThat(snapshot.retryCount()).isNull();
        assertThat(snapshot.activityRetrying()).isFalse();
    }

    @Test
    void shouldSkipSystemAndLegacyDispatch() {
        assertThat(ExecutionMirrorTickActivitiesImpl.shouldSkipWorkflow("sys-execution-mirror-1", "X"))
                .isTrue();
        assertThat(ExecutionMirrorTickActivitiesImpl.shouldSkipWorkflow("wf-a-1", "ExecutionMirrorTickWorkflow"))
                .isTrue();
        assertThat(ExecutionMirrorTickActivitiesImpl.shouldSkipWorkflow("wf-a-1", "JobDispatchWorkflow"))
                .isTrue();
        assertThat(ExecutionMirrorTickActivitiesImpl.shouldSkipWorkflow("wf-log_count_tick-1", "LogCountTickWorkflow"))
                .isFalse();
    }

    @Test
    void toResultJson_wrapsScalar() {
        assertThat(ExecutionMirrorTickActivitiesImpl.toResultJson(12)).contains("\"value\"");
        assertThat(ExecutionMirrorTickActivitiesImpl.toResultJson(null)).isNull();
    }

    @Test
    void extractInputFromResult_readsInputKey() {
        assertThat(ExecutionMirrorTickActivitiesImpl.extractInputFromResult(
                        Map.of("count", 1L, "input", Map.of("trigger", "manual"))))
                .contains("\"trigger\"")
                .contains("manual");
        assertThat(ExecutionMirrorTickActivitiesImpl.extractInputFromResult(Map.of("count", 1L)))
                .isNull();
        assertThat(ExecutionMirrorTickActivitiesImpl.extractInputFromResult(null))
                .isNull();
        assertThat(ExecutionMirrorTickActivitiesImpl.decodeStartedInput(null, null))
                .isNull();
    }
}

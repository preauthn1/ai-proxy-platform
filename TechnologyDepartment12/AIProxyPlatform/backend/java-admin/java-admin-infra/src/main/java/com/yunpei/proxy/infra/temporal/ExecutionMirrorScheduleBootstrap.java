package com.yunpei.proxy.infra.temporal;

import com.yunpei.proxy.service.task.TemporalTaskQueue;
import com.yunpei.proxy.service.task.TemporalWorkflowType;
import io.temporal.api.enums.v1.ScheduleOverlapPolicy;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.schedules.Schedule;
import io.temporal.client.schedules.ScheduleActionStartWorkflow;
import io.temporal.client.schedules.ScheduleAlreadyRunningException;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleHandle;
import io.temporal.client.schedules.ScheduleIntervalSpec;
import io.temporal.client.schedules.ScheduleOptions;
import io.temporal.client.schedules.SchedulePolicy;
import io.temporal.client.schedules.ScheduleSpec;
import io.temporal.client.schedules.ScheduleState;
import io.temporal.client.schedules.ScheduleUpdate;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时确保系统 Schedule {@code sys-execution-mirror} 存在：每 3s 跑一轮
 * {@link TemporalWorkflowType#EXECUTION_MIRROR_TICK}，overlap=SKIP（多实例单飞）。
 *
 * @author yunpei
 */
@Component
@Order(200)
public class ExecutionMirrorScheduleBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExecutionMirrorScheduleBootstrap.class);

    public static final String SCHEDULE_ID = "sys-execution-mirror";
    public static final String WORKFLOW_ID_PREFIX = "sys-execution-mirror";
    public static final Duration INTERVAL = Duration.ofSeconds(3);

    private final ScheduleClient scheduleClient;

    public ExecutionMirrorScheduleBootstrap(ScheduleClient scheduleClient) {
        this.scheduleClient = scheduleClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            ensureSchedule();
        } catch (RuntimeException ex) {
            // 不阻断启动：镜像可后续手动补 Schedule
            log.atWarn()
                    .addKeyValue("scheduleId", SCHEDULE_ID)
                    .addKeyValue("reason", TemporalScheduleErrors.formatReason(ex))
                    .setCause(ex)
                    .log("ExecutionMirror schedule bootstrap failed");
        }
    }

    void ensureSchedule() {
        Schedule schedule = buildSchedule();
        ScheduleHandle handle = scheduleClient.getHandle(SCHEDULE_ID);
        if (exists(handle)) {
            handle.update(input -> new ScheduleUpdate(schedule));
            if (isPaused(handle)) {
                handle.unpause("execution mirror enabled");
            }
            log.atInfo()
                    .addKeyValue("scheduleId", SCHEDULE_ID)
                    .addKeyValue("intervalSeconds", INTERVAL.toSeconds())
                    .log("ExecutionMirror schedule updated");
            return;
        }
        try {
            scheduleClient.createSchedule(
                    SCHEDULE_ID,
                    schedule,
                    ScheduleOptions.newBuilder().setTriggerImmediately(true).build());
            log.atInfo()
                    .addKeyValue("scheduleId", SCHEDULE_ID)
                    .addKeyValue("intervalSeconds", INTERVAL.toSeconds())
                    .log("ExecutionMirror schedule created");
        } catch (ScheduleAlreadyRunningException ex) {
            handle.update(input -> new ScheduleUpdate(schedule));
            if (isPaused(handle)) {
                handle.unpause("execution mirror enabled");
            }
            log.atInfo()
                    .addKeyValue("scheduleId", SCHEDULE_ID)
                    .log("ExecutionMirror schedule created-raced-then-updated");
        }
    }

    private static Schedule buildSchedule() {
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(WORKFLOW_ID_PREFIX)
                .setTaskQueue(TemporalTaskQueue.SYSTEM)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(3))
                .build();

        ScheduleActionStartWorkflow action = ScheduleActionStartWorkflow.newBuilder()
                .setWorkflowType(TemporalWorkflowType.EXECUTION_MIRROR_TICK)
                .setOptions(options)
                .build();

        ScheduleSpec spec = ScheduleSpec.newBuilder()
                .setIntervals(List.of(new ScheduleIntervalSpec(INTERVAL)))
                .build();

        SchedulePolicy policy = SchedulePolicy.newBuilder()
                .setOverlap(ScheduleOverlapPolicy.SCHEDULE_OVERLAP_POLICY_SKIP)
                .build();

        ScheduleState state = ScheduleState.newBuilder()
                .setPaused(false)
                .setNote("system execution mirror tick")
                .build();

        return Schedule.newBuilder()
                .setAction(action)
                .setSpec(spec)
                .setPolicy(policy)
                .setState(state)
                .build();
    }

    private static boolean exists(ScheduleHandle handle) {
        try {
            handle.describe();
            return true;
        } catch (RuntimeException ex) {
            if (TemporalScheduleErrors.isNotFound(ex)) {
                return false;
            }
            throw ex;
        }
    }

    private static boolean isPaused(ScheduleHandle handle) {
        try {
            ScheduleState state = handle.describe().getSchedule().getState();
            return state != null && state.isPaused();
        } catch (RuntimeException ex) {
            if (TemporalScheduleErrors.isNotFound(ex)) {
                return false;
            }
            throw ex;
        }
    }

    /** @see TemporalScheduleErrors#isNotFound(Throwable) */
    static boolean isNotFound(Throwable error) {
        return TemporalScheduleErrors.isNotFound(error);
    }
}

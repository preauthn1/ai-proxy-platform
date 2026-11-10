package com.yunpei.proxy.infra.temporal;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.temporal.client.schedules.ScheduleException;
import org.junit.jupiter.api.Test;

class TemporalScheduleErrorsTest {

    @Test
    void isNotFound_statusRuntimeException() {
        assertThat(TemporalScheduleErrors.isNotFound(
                        new StatusRuntimeException(Status.NOT_FOUND.withDescription("missing"))))
                .isTrue();
        assertThat(TemporalScheduleErrors.isNotFound(
                        new StatusRuntimeException(Status.UNAVAILABLE.withDescription("down"))))
                .isFalse();
    }

    @Test
    void isNotFound_wrappedScheduleException() {
        ScheduleException ex = new ScheduleException(new StatusRuntimeException(
                Status.NOT_FOUND.withDescription("workflow not found for ID: temporal-sys-scheduler:task-x")));
        assertThat(TemporalScheduleErrors.isNotFound(ex)).isTrue();
    }

    @Test
    void isTransient_unavailableAndDeadline() {
        assertThat(TemporalScheduleErrors.isTransient(
                        new ScheduleException(new StatusRuntimeException(Status.UNAVAILABLE))))
                .isTrue();
        assertThat(TemporalScheduleErrors.isTransient(
                        new ScheduleException(new StatusRuntimeException(Status.DEADLINE_EXCEEDED))))
                .isTrue();
        assertThat(TemporalScheduleErrors.isTransient(
                        new ScheduleException(new StatusRuntimeException(Status.NOT_FOUND))))
                .isFalse();
        // 无 gRPC 状态的普通异常不可重试（Status.fromThrowable 会落成 UNKNOWN）
        assertThat(TemporalScheduleErrors.isTransient(new RuntimeException("boom")))
                .isFalse();
    }

    @Test
    void formatReason_includesGrpcStatus() {
        ScheduleException ex = new ScheduleException(
                new StatusRuntimeException(Status.UNAVAILABLE.withDescription("connection refused")));
        String reason = TemporalScheduleErrors.formatReason(ex);
        assertThat(reason)
                .contains("Schedule exception")
                .contains("UNAVAILABLE")
                .contains("connection refused");
    }
}

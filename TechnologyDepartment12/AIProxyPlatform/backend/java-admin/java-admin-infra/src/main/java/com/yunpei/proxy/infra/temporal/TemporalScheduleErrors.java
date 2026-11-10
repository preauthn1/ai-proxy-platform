package com.yunpei.proxy.infra.temporal;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.temporal.client.schedules.ScheduleException;
import java.util.Locale;

/**
 * Temporal Schedule 客户端异常工具：识别 NOT_FOUND、瞬时错误，并格式化可读根因。
 *
 * <p>SDK 的 {@link ScheduleException} 固定 message 为 {@code "Schedule exception"}，
 * 真实 gRPC 状态在 cause 链中；日志若只打 {@code ex.getMessage()} 会丢失诊断信息。
 *
 * @author yunpei
 */
final class TemporalScheduleErrors {

    private TemporalScheduleErrors() {}

    /**
     * 判断是否为「Schedule 不存在」。
     *
     * <p>优先用 {@link Status#fromThrowable(Throwable)}（遍历 cause 链提取 gRPC 状态），
     * 再兜底匹配 message 中的 {@code not found}。
     */
    static boolean isNotFound(Throwable error) {
        if (error == null) {
            return false;
        }
        if (Status.fromThrowable(error).getCode() == Status.Code.NOT_FOUND) {
            return true;
        }
        Throwable cursor = error;
        while (cursor != null) {
            String msg = cursor.getMessage();
            if (msg != null && msg.toLowerCase(Locale.ROOT).contains("not found")) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    /**
     * 是否为可重试的瞬时 gRPC 错误（连接未就绪、超时、资源耗尽等）。
     *
     * <p>注意：{@link Status#fromThrowable} 在无 gRPC 状态时返回 {@code UNKNOWN}，
     * 不能据此重试普通 {@link RuntimeException}。
     */
    static boolean isTransient(Throwable error) {
        if (error == null || !hasGrpcStatus(error)) {
            return false;
        }
        Status.Code code = Status.fromThrowable(error).getCode();
        return code == Status.Code.UNAVAILABLE
                || code == Status.Code.DEADLINE_EXCEEDED
                || code == Status.Code.RESOURCE_EXHAUSTED
                || code == Status.Code.ABORTED;
    }

    /** cause 链上是否存在 gRPC {@link StatusRuntimeException}/{@link StatusException}。 */
    static boolean hasGrpcStatus(Throwable error) {
        Throwable cursor = error;
        while (cursor != null) {
            if (cursor instanceof StatusRuntimeException || cursor instanceof StatusException) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    /**
     * 格式化一行可读根因，供 WARN 的 reason 字段使用。
     *
     * <p>示例：{@code Schedule exception | NOT_FOUND: workflow not found for ID: ...}
     */
    static String formatReason(Throwable error) {
        if (error == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        String top = error.getMessage();
        if (top != null && !top.isBlank()) {
            sb.append(top.trim());
        } else {
            sb.append(error.getClass().getSimpleName());
        }

        Status status = Status.fromThrowable(error);
        if (status.getCode() != Status.Code.OK) {
            // UNKNOWN 且无 description 时仍附上 code，便于区分「无 gRPC 状态」的纯本地异常
            boolean hasDesc =
                    status.getDescription() != null && !status.getDescription().isBlank();
            if (status.getCode() != Status.Code.UNKNOWN || hasDesc) {
                sb.append(" | ").append(status.getCode());
                if (hasDesc) {
                    sb.append(": ").append(status.getDescription().trim());
                }
            }
        }

        Throwable root = rootCause(error);
        if (root != null && root != error) {
            String rootMsg = root.getMessage();
            if (rootMsg != null
                    && !rootMsg.isBlank()
                    && (status.getDescription() == null || !rootMsg.contains(status.getDescription()))) {
                sb.append(" | root=")
                        .append(root.getClass().getSimpleName())
                        .append(": ")
                        .append(rootMsg.trim());
            }
        }
        return sb.toString();
    }

    static Throwable rootCause(Throwable error) {
        Throwable cursor = error;
        while (cursor != null && cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        return cursor;
    }
}

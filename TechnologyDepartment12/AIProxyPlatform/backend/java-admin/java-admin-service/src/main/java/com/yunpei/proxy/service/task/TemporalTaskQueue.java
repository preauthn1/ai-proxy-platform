package com.yunpei.proxy.service.task;

import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.ResultCode;
import java.util.List;

/**
 * 已注册 Temporal task queue 常量（与 {@code @WorkflowImpl(taskQueues=...)} 对齐）。
 *
 * <p>使用常量类而非枚举，便于注解引用编译期常量；任务配置 create/update 时必须命中
 * {@link #requireCode(String)}（仅校验 {@link #ALL}）。新增<strong>业务</strong>队列时在此登记并加入
 * {@link #ALL}；系统队列仅声明常量、不进 {@link #ALL}（对齐 {@link TemporalWorkflowType}）。
 *
 * @author yunpei
 */
public final class TemporalTaskQueue {

    /** 业务 / 联调队列（{@code LogCountTick} 等可被任务配置选用）。 */
    public static final String DEMO = "demo";

    /**
     * 系统内部队列（{@code ExecutionMirrorTick} 等）。
     *
     * <p><b>不进入</b> {@link #ALL}，任务配置 create/update 不可选用；由系统 Schedule / Worker 注解绑定。
     */
    public static final String SYSTEM = "system";

    /**
     * 图片生成专属队列（{@code ImageGenerationWorkflow} / Activity）。
     *
     * <p><b>不进入</b> {@link #ALL}：仅由生图 Worker / TriggerPort 绑定；任务配置 create/update 不可选用。
     * 并发由 {@code spring.temporal.workers[task-queue=image-generation].capacity} 控制。
     */
    public static final String IMAGE_GENERATION = "image-generation";

    /**
     * 视频生成专属队列（{@code VideoGenerationWorkflow} / Activity）。
     *
     * <p><b>不进入</b> {@link #ALL}：仅由视频 Worker / TriggerPort 绑定。
     */
    public static final String VIDEO_GENERATION = "video-generation";

    /**
     * AI 文本生成专属队列（{@code AiTextGenerationWorkflow} / Activity）。
     *
     * <p><b>不进入</b> {@link #ALL}：仅由 AI Worker / TriggerPort 绑定。
     */
    public static final String AI_TEXT_GENERATION = "ai-text-generation";

    /**
     * 广告效果分析专属队列（{@code AdPerformanceAnalysisWorkflow} / Activity）。
     *
     * <p><b>不进入</b> {@link #ALL}：仅由分析 Worker / TriggerPort 绑定。
     */
    public static final String AD_PERFORMANCE_ANALYSIS = "ad-performance-analysis";

    /** 全部合法<strong>业务</strong> task queue（任务配置门禁与下拉）。 */
    public static final List<String> ALL = List.of(DEMO);

    private TemporalTaskQueue() {}

    /**
     * 解析合法 task queue；未知值抛业务异常。
     *
     * @param raw 请求中的 taskQueue
     * @return 规范化后的常量值
     */
    public static String requireCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "taskQueue is required");
        }
        String trimmed = raw.trim();
        for (String allowed : ALL) {
            if (allowed.equalsIgnoreCase(trimmed)) {
                return allowed;
            }
        }
        throw BizException.of(
                ResultCode.PARAM_INVALID, "unknown taskQueue: " + trimmed + "; allowed=" + String.join(",", ALL));
    }

    /** 是否已登记。 */
    public static boolean isKnown(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String trimmed = raw.trim();
        for (String allowed : ALL) {
            if (allowed.equalsIgnoreCase(trimmed)) {
                return true;
            }
        }
        return false;
    }
}

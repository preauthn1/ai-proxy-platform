package com.yunpei.proxy.service.task;

import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.ResultCode;
import java.util.List;

/**
 * 已注册 Temporal Workflow 类型常量（与 {@code @WorkflowMethod(name=...)} 对齐）。
 *
 * <p>使用常量类而非枚举，便于注解引用编译期常量；任务配置 create/update 时必须命中
 * {@link #requireCode(String)}。新增 Workflow 时在此登记并加入 {@link #ALL}。
 *
 * @author yunpei
 */
public final class TemporalWorkflowType {

    /** 日志计数 tick（dev 测试 / 当前唯一已注册业务 Workflow）。 */
    public static final String LOG_COUNT_TICK = "LogCountTickWorkflow";

    /**
     * 系统镜像 tick：定时 list/describe Temporal 执行并 upsert {@code temporal_task_execution}。
     *
     * <p><b>不进入</b> {@link #ALL}，任务配置 create/update 不可选用；由系统 Schedule 驱动。
     */
    public static final String EXECUTION_MIRROR_TICK = "ExecutionMirrorTickWorkflow";

    /**
     * 外部图片生成 Workflow（{@code ImageGenerationWorkflow}）。
     *
     * <p><b>不进入</b> {@link #ALL}：仅由 {@code ImageGenerationTriggerPort} 直启，任务配置
     * create/update 不可选用（入参契约为固定 {@code jobId}，与通用任务配置不兼容）。
     * <b>进入</b> {@link #FOR_MIRROR}，供 ExecutionMirrorTick Visibility 扫描兜底。
     */
    public static final String IMAGE_GENERATION = "ImageGenerationWorkflow";

    /**
     * 外部视频生成 Workflow（{@code VideoGenerationWorkflow}）。
     *
     * <p><b>不进入</b> {@link #ALL}：仅由 {@code VideoGenerationTriggerPort} 直启。
     * <b>进入</b> {@link #FOR_MIRROR}，供 ExecutionMirrorTick Visibility 扫描兜底。
     */
    public static final String VIDEO_GENERATION = "VideoGenerationWorkflow";

    /**
     * 外部 AI 文本生成 Workflow（单一入口，按 task_type 在服务内分发）。
     *
     * <p><b>不进入</b> {@link #ALL}：仅由 {@code AiGenerationTriggerPort} 直启。
     * <b>进入</b> {@link #FOR_MIRROR}，供 ExecutionMirrorTick Visibility 扫描兜底。
     */
    public static final String AI_TEXT_GENERATION = "AiTextGenerationWorkflow";

    /**
     * 外部广告效果分析 Workflow。
     *
     * <p><b>不进入</b> {@link #ALL}：仅由 {@code AdPerformanceAnalysisTriggerPort} 直启。
     * <b>进入</b> {@link #FOR_MIRROR}。
     */
    public static final String AD_PERFORMANCE_ANALYSIS = "AdPerformanceAnalysisWorkflow";

    /** 全部合法业务 workflow type（任务配置门禁）。 */
    public static final List<String> ALL = List.of(LOG_COUNT_TICK);

    /**
     * ExecutionMirrorTick Visibility 扫描的 workflow type 列表。
     *
     * <p>含任务配置可选用类型（{@link #ALL}）以及直启、不进配置门禁的业务类型
     * （如 {@link #IMAGE_GENERATION} / {@link #VIDEO_GENERATION} / {@link #AI_TEXT_GENERATION}
     * / {@link #AD_PERFORMANCE_ANALYSIS}）。
     * 系统类型（如 {@link #EXECUTION_MIRROR_TICK}）不入此列表。
     */
    public static final List<String> FOR_MIRROR =
            List.of(LOG_COUNT_TICK, IMAGE_GENERATION, VIDEO_GENERATION, AI_TEXT_GENERATION, AD_PERFORMANCE_ANALYSIS);

    private TemporalWorkflowType() {}

    /**
     * 解析合法 workflow type；未知值抛业务异常。
     *
     * @param raw 请求中的 workflowType
     * @return 规范化后的常量值（与登记字面量大小写一致）
     */
    public static String requireCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "workflowType is required");
        }
        String trimmed = raw.trim();
        for (String allowed : ALL) {
            if (allowed.equalsIgnoreCase(trimmed)) {
                return allowed;
            }
        }
        throw BizException.of(
                ResultCode.PARAM_INVALID, "unknown workflowType: " + trimmed + "; allowed=" + String.join(",", ALL));
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

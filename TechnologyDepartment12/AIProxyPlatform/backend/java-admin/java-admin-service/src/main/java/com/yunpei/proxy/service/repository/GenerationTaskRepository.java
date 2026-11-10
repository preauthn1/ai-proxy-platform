package com.yunpei.proxy.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.yunpei.proxy.service.entity.GenerationTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 图片生成任务 Repository。
 *
 * <p>软删过滤由 {@code BaseEntity#deletedAt} 的 {@code @LogicDelete} 自动附加。
 *
 * @author yunpei
 */
@Component
@RequiredArgsConstructor
public final class GenerationTaskRepository {

    public static final String TASK_TYPE_EXTERNAL_IMAGE_GENERATE = "external_image_generate";
    public static final String BUSINESS_TYPE_EXTERNAL_IMAGE = "external_image";
    public static final String TASK_TYPE_EXTERNAL_VIDEO_GENERATE = "external_video_generate";
    public static final String BUSINESS_TYPE_EXTERNAL_VIDEO = "external_video";

    /** 外部 AI 文本类 business_type。 */
    public static final String BUSINESS_TYPE_EXTERNAL_AI = "external_ai";
    /** 工单分析 V3（主路径 /work-order-analysis）。 */
    public static final String TASK_TYPE_EXTERNAL_WORK_ORDER_ANALYSIS_V3 = "external_work_order_analysis_v3";
    /** 选题生成。 */
    public static final String TASK_TYPE_EXTERNAL_TOPIC_SELECTION = "external_topic_selection";
    /** 文案生成。 */
    public static final String TASK_TYPE_EXTERNAL_COPY_GENERATION = "external_copy_generation";
    /** 视频分镜 V1。 */
    public static final String TASK_TYPE_EXTERNAL_VIDEO_STORYBOARD = "external_video_storyboard";

    /** 外部广告效果分析。 */
    public static final String TASK_TYPE_AD_PERFORMANCE_ANALYSIS = "ad_performance_analysis";
    /** 外部广告效果分析 business_type。 */
    public static final String BUSINESS_TYPE_AD_PERFORMANCE_ANALYSIS = "ad_performance_analysis";

    public static final String STATUS_QUEUED = "queued";
    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_SUCCEEDED = "succeeded";
    public static final String STATUS_FAILED = "failed";

    private final EasyEntityQuery easyEntityQuery;

    public GenerationTask findById(String id) {
        if (id == null) {
            return null;
        }
        return easyEntityQuery
                .queryable(GenerationTask.class)
                .where(t -> t.id().eq(id))
                .firstOrNull();
    }

    /**
     * 幂等查询：按 external_request_id（落在 {@code business_id}）+ task_type + business_type 查唯一任务。
     *
     * <p>对齐 Python：{@code business_id = external_request_id or uuid}，故 external_request_id 存在时落在 business_id。
     *
     * @param externalRequestId external_request_id；null/空 → 返回 null（不做幂等）
     */
    public GenerationTask findByExternalRequestIdAndType(
            String externalRequestId, String taskType, String businessType) {
        if (externalRequestId == null || externalRequestId.isBlank()) {
            return null;
        }
        return easyEntityQuery
                .queryable(GenerationTask.class)
                .where(t -> {
                    t.taskType().eq(taskType);
                    t.businessType().eq(businessType);
                    t.businessId().eq(externalRequestId);
                })
                .firstOrNull();
    }

    public void insert(GenerationTask row) {
        // id 为业务生成的 UUID（非自增），无需回填自增键，故传 false
        easyEntityQuery.insertable(row).executeRows();
    }

    /**
     * 更新任务状态（不限制原状态；广告分析从 queued 推进到 processing 时使用）。
     */
    public long updateStatus(String id, String status) {
        return easyEntityQuery
                .updatable(GenerationTask.class)
                .setColumns(t -> t.status().set(status))
                .where(t -> t.id().eq(id))
                .executeRows();
    }

    /**
     * 将任务标记为 {@code succeeded} 并回写结果 JSON。
     *
     * <p>更新条件叠加 {@code status=processing}，与 {@code ExternalImageGenerationService#execute} 的
     * 状态检查共同形成幂等：并发/重试下第二个实例的 {@code status=processing} 条件不再命中，
     * 不会重复覆盖或重复出图。
     *
     * @param id 任务 id
     * @param resultJson 组装好的结果 JSON（images/count/size/model_id）
     * @return 影响行数（0=状态已非 processing，未更新）
     */
    public long markSucceeded(String id, String resultJson) {
        return easyEntityQuery
                .updatable(GenerationTask.class)
                .setColumns(t -> {
                    t.status().set(STATUS_SUCCEEDED);
                    t.resultJson().set(resultJson);
                })
                .where(t -> t.id().eq(id))
                .where(t -> t.status().eq(STATUS_PROCESSING))
                .executeRows();
    }

    /**
     * 将任务标记为 {@code failed} 并记录错误码/错误信息。
     *
     * <p>同样叠加 {@code status=processing} 条件，保证幂等，不覆盖已成功/已失败任务。
     *
     * @param id 任务 id
     * @param errorCode 错误码（如 {@code provider_error}）
     * @param errorMessage 错误信息（截断到 512 字符以内）
     * @return 影响行数（0=状态已非 processing，未更新）
     */
    public long markFailed(String id, String errorCode, String errorMessage) {
        return easyEntityQuery
                .updatable(GenerationTask.class)
                .setColumns(t -> {
                    t.status().set(STATUS_FAILED);
                    t.errorCode().set(errorCode);
                    t.errorMessage().set(errorMessage);
                })
                .where(t -> t.id().eq(id))
                .where(t -> t.status().eq(STATUS_PROCESSING))
                .executeRows();
    }

    /**
     * 更新 metadata_json（用于写入 provider_job_id 等；不改 status）。
     *
     * @param id 任务 id
     * @param metadataJson 元数据 JSON
     * @return 影响行数
     */
    public long updateMetadataJson(String id, String metadataJson) {
        return easyEntityQuery
                .updatable(GenerationTask.class)
                .setColumns(t -> t.metadataJson().set(metadataJson))
                .where(t -> t.id().eq(id))
                .executeRows();
    }

    /**
     * 更新 payload_json（例如视频 start 阶段写回内部分镜文案；不改 status）。
     *
     * @param id 任务 id
     * @param payloadJson 载荷 JSON
     * @return 影响行数
     */
    public long updatePayloadJson(String id, String payloadJson) {
        return easyEntityQuery
                .updatable(GenerationTask.class)
                .setColumns(t -> t.payloadJson().set(payloadJson))
                .where(t -> t.id().eq(id))
                .executeRows();
    }
}

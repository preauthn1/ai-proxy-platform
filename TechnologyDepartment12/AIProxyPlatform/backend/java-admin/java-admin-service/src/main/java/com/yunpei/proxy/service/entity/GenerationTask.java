package com.yunpei.proxy.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.yunpei.proxy.service.entity.proxy.GenerationTaskProxy;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 图片生成任务实体（对齐 schema v11 {@code generation_task}，spec §3.1）。
 *
 * <p>扩展 {@link BaseEntity}：审计字段 + {@code @LogicDelete} 软删（{@code deleted_at}）。
 * {@code payload_json} / {@code result_json} / {@code metadata_json} 为 MySQL JSON 列，实体层以字符串承载，
 * 序列化/解析由 {@code ExternalImageGenerationService} 完成。
 *
 * <p>{@code id} 为主键但不自增：由业务层生成 UUID（{@code VARCHAR(64)}）。
 *
 * @author yunpei
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("generation_task")
@EntityProxy
public class GenerationTask extends BaseEntity implements ProxyEntityAvailable<GenerationTask, GenerationTaskProxy> {

    @Column(primaryKey = true)
    private String id;

    /** 任务类型；默认 {@code external_image_generate}。 */
    private String taskType;

    /** 业务类型；默认 {@code external_image}。 */
    private String businessType;

    /** 业务 id；= external_request_id（存在时），否则 = job_id。 */
    private String businessId;

    /** 任务队列名（Temporal 用；T1 不触发）。 */
    private String queueName;

    /** 状态：processing | succeeded | failed。 */
    private String status;

    /** 入参 JSON（prompt/count/size/model_id/external_request_id）。 */
    private String payloadJson;

    /** 结果 JSON（images 等）。 */
    private String resultJson;

    /** 元数据 JSON（source/external_request_id/count/size/model_id）。 */
    private String metadataJson;

    private String errorCode;

    private String errorMessage;

    private Integer maxAttempts;

    private Integer attemptCount;
}

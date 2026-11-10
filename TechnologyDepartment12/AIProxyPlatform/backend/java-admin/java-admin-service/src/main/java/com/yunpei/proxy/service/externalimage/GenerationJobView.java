package com.yunpei.proxy.service.externalimage;

import java.util.List;

/**
 * 任务详情视图（GET 用；controller 据此组装信封与 VO）。
 *
 * @param jobId 任务 id
 * @param status succeeded | failed | processing
 * @param count 图数量
 * @param size 尺寸
 * @param modelId 模型 id（可为 null）
 * @param images 生成结果图；非 succeeded 为空
 * @param error 对外错误文案（failed 时）
 * @param sourceJobId revision 源 job；可空
 * @param mode generate / edit / from_image；可空
 * @author yunpei
 */
public record GenerationJobView(
        String jobId,
        String status,
        int count,
        String size,
        String modelId,
        List<GeneratedImageItem> images,
        String error,
        String sourceJobId,
        String mode) {}

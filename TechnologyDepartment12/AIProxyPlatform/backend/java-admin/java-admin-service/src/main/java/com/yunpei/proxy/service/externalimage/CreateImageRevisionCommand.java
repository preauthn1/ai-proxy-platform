package com.yunpei.proxy.service.externalimage;

/**
 * 基于已成功 job 的修订入参。
 *
 * @param sourceJobId 源任务 id
 * @param feedback 修改要求
 * @param sourceImageIndex 源图 1-based 下标；默认 1
 * @param count 覆盖 count；可空则继承源
 * @param size 覆盖 size；可空则继承源
 * @param modelId 覆盖模型；可空则继承源
 * @param externalRequestId 幂等键
 * @author yunpei
 */
public record CreateImageRevisionCommand(
        String sourceJobId,
        String feedback,
        Integer sourceImageIndex,
        Integer count,
        String size,
        String modelId,
        String externalRequestId) {}

package com.yunpei.proxy.service.externalimage;

/**
 * 建任务结果（供 controller 组装创建信封）。
 *
 * @param jobId 任务 id
 * @param status 创建信封侧恒为 processing
 * @param count 实际 count
 * @param size 实际 size
 * @param mode generate / edit / from_image；可空
 * @param sourceJobId revision 时的源 job；可空
 * @author yunpei
 */
public record CreateJobResult(String jobId, String status, int count, String size, String mode, String sourceJobId) {

    public static CreateJobResult of(String jobId, String status, int count, String size) {
        return new CreateJobResult(jobId, status, count, size, null, null);
    }
}

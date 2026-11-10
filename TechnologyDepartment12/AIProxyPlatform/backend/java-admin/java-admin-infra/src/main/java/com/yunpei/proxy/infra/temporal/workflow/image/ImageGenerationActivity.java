package com.yunpei.proxy.infra.temporal.workflow.image;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import java.util.Map;

/**
 * 图片生成 Activity：执行 {@code ExternalImageGenerationService#execute(jobId)}，完成生成与状态回写，
 * 并将业务结果 Map 交回 Workflow（供执行镜像 {@code result_summary}）。
 *
 * @author yunpei
 */
@ActivityInterface
public interface ImageGenerationActivity {

    /**
     * 执行一次图片生成并回写状态。
     *
     * @param jobId 图片生成任务 id
     * @return 与 {@code generation_task.result_json} 对齐的结果 Map；失败时含 error 字段
     */
    @ActivityMethod
    Map<String, Object> execute(String jobId);
}

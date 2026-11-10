package com.yunpei.proxy.infra.temporal.workflow.video;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import java.util.Map;

/**
 * 视频生成 Activity：start 上游 + 单次 poll。
 *
 * @author yunpei
 */
@ActivityInterface
public interface VideoGenerationActivity {

    /** 调用 Provider start，写入 provider_job_id。 */
    @ActivityMethod
    Map<String, Object> start(String jobId);

    /** 查询上游一次并在终态时回写 generation_task。 */
    @ActivityMethod
    Map<String, Object> poll(String jobId);
}

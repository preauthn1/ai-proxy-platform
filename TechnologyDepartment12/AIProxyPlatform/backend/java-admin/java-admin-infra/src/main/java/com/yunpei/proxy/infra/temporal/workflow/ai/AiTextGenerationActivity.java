package com.yunpei.proxy.infra.temporal.workflow.ai;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import java.util.Map;

/**
 * AI 文本生成 Activity：委托 {@code ExternalAiGenerationService#execute}。
 *
 * @author yunpei
 */
@ActivityInterface
public interface AiTextGenerationActivity {

    /**
     * 执行一次 AI 文本任务并回写状态。
     *
     * @param jobId 任务 id
     * @return 业务结果 Map
     */
    @ActivityMethod
    Map<String, Object> execute(String jobId);
}

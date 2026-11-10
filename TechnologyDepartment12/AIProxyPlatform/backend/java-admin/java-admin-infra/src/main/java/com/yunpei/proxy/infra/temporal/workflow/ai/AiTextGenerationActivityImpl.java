package com.yunpei.proxy.infra.temporal.workflow.ai;

import com.yunpei.proxy.service.externalai.ExternalAiGenerationService;
import com.yunpei.proxy.service.llm.LlmTimeoutException;
import io.temporal.activity.Activity;
import io.temporal.spring.boot.ActivityImpl;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * {@link AiTextGenerationActivity} 实现：委托 {@link ExternalAiGenerationService#execute}。
 *
 * @author yunpei
 */
@Component
@ActivityImpl(taskQueues = AiTextGenerationWorkflowImpl.TASK_QUEUE)
public class AiTextGenerationActivityImpl implements AiTextGenerationActivity {

    private final ExternalAiGenerationService service;

    public AiTextGenerationActivityImpl(ExternalAiGenerationService service) {
        this.service = service;
    }

    @Override
    public Map<String, Object> execute(String jobId) {
        try {
            return service.execute(jobId);
        } catch (LlmTimeoutException ex) {
            int attempt = Activity.getExecutionContext().getInfo().getAttempt();
            if (attempt >= AiTextGenerationWorkflowImpl.ACTIVITY_MAX_ATTEMPTS) {
                return service.recordProviderFailure(jobId, ex.getMessage());
            }
            throw ex;
        }
    }
}

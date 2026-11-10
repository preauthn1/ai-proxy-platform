package com.yunpei.proxy.infra.temporal.workflow.image;

import com.yunpei.proxy.service.externalimage.ExternalImageGenerationService;
import io.temporal.spring.boot.ActivityImpl;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * {@link ImageGenerationActivity} 实现：委托 {@link ExternalImageGenerationService#execute}，
 * 返回业务结果 Map 作为 Temporal Activity 输出。
 *
 * <p>由 {@code spring.temporal.workers-auto-discovery.register-activity-beans} 自动注册到
 * {@code image-generation} 队列，无需显式装配。
 *
 * @author yunpei
 */
@Component
@ActivityImpl(taskQueues = ImageGenerationWorkflowImpl.TASK_QUEUE)
public class ImageGenerationActivityImpl implements ImageGenerationActivity {

    private final ExternalImageGenerationService service;

    public ImageGenerationActivityImpl(ExternalImageGenerationService service) {
        this.service = service;
    }

    @Override
    public Map<String, Object> execute(String jobId) {
        return service.execute(jobId);
    }
}

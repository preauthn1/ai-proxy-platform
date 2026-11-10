package com.yunpei.proxy.infra.temporal.workflow.video;

import com.yunpei.proxy.service.externalvideo.ExternalVideoGenerationService;
import io.temporal.spring.boot.ActivityImpl;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * {@link VideoGenerationActivity} 实现。
 *
 * @author yunpei
 */
@Component
@ActivityImpl(taskQueues = VideoGenerationWorkflowImpl.TASK_QUEUE)
public class VideoGenerationActivityImpl implements VideoGenerationActivity {

    private final ExternalVideoGenerationService service;

    public VideoGenerationActivityImpl(ExternalVideoGenerationService service) {
        this.service = service;
    }

    @Override
    public Map<String, Object> start(String jobId) {
        return service.startProvider(jobId);
    }

    @Override
    public Map<String, Object> poll(String jobId) {
        return service.pollProvider(jobId);
    }
}

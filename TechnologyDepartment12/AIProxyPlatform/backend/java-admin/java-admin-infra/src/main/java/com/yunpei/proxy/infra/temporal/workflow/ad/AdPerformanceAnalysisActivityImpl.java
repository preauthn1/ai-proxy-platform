package com.yunpei.proxy.infra.temporal.workflow.ad;

import com.yunpei.proxy.service.externalad.ExternalAdPerformanceAnalysisService;
import io.temporal.spring.boot.ActivityImpl;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Activity 实现：委托 {@link ExternalAdPerformanceAnalysisService#execute}。
 *
 * @author yunpei
 */
@Component
@ActivityImpl(taskQueues = AdPerformanceAnalysisWorkflowImpl.TASK_QUEUE)
public class AdPerformanceAnalysisActivityImpl implements AdPerformanceAnalysisActivity {

    private final ExternalAdPerformanceAnalysisService service;

    public AdPerformanceAnalysisActivityImpl(ExternalAdPerformanceAnalysisService service) {
        this.service = service;
    }

    @Override
    public Map<String, Object> execute(String analysisId) {
        return service.execute(analysisId);
    }
}

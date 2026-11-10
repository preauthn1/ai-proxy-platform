package com.yunpei.proxy.infra.temporal.workflow.ad;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import java.util.Map;

/**
 * 广告效果分析 Activity：执行 {@code ExternalAdPerformanceAnalysisService#execute}。
 *
 * @author yunpei
 */
@ActivityInterface
public interface AdPerformanceAnalysisActivity {

    /**
     * 推进一次分析任务。
     *
     * @param analysisId analysis_id
     * @return 状态摘要
     */
    @ActivityMethod
    Map<String, Object> execute(String analysisId);
}

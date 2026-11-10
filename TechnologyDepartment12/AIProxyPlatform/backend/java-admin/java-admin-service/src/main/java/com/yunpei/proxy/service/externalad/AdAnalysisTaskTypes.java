package com.yunpei.proxy.service.externalad;

import com.yunpei.proxy.service.repository.GenerationTaskRepository;

/**
 * 外部广告效果分析任务常量。
 *
 * @author yunpei
 */
public final class AdAnalysisTaskTypes {

    public static final String TASK_TYPE = GenerationTaskRepository.TASK_TYPE_AD_PERFORMANCE_ANALYSIS;
    public static final String BUSINESS_TYPE = GenerationTaskRepository.BUSINESS_TYPE_AD_PERFORMANCE_ANALYSIS;
    public static final String SCOPE = "facebook_ad_performance";
    public static final String SOURCE = "external_ad_performance_analysis";

    public static final String STATUS_QUEUED = "queued";
    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_SUCCEEDED = "succeeded";
    public static final String STATUS_FAILED = "failed";

    private AdAnalysisTaskTypes() {}

    public static boolean isAdAnalysisTask(String taskType, String businessType) {
        return TASK_TYPE.equals(taskType) && BUSINESS_TYPE.equals(businessType);
    }
}

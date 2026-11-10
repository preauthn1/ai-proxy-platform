package com.yunpei.proxy.service.externalai;

import com.yunpei.proxy.service.repository.GenerationTaskRepository;
import java.util.Set;

/**
 * 外部 AI 文本类 task_type 常量与命中集合（GET jobs 仅允许本集合）。
 *
 * @author yunpei
 */
public final class ExternalAiTaskTypes {

    public static final String BUSINESS_TYPE = GenerationTaskRepository.BUSINESS_TYPE_EXTERNAL_AI;

    public static final String WORK_ORDER_ANALYSIS_V3 =
            GenerationTaskRepository.TASK_TYPE_EXTERNAL_WORK_ORDER_ANALYSIS_V3;
    public static final String TOPIC_SELECTION = GenerationTaskRepository.TASK_TYPE_EXTERNAL_TOPIC_SELECTION;
    public static final String COPY_GENERATION = GenerationTaskRepository.TASK_TYPE_EXTERNAL_COPY_GENERATION;
    public static final String VIDEO_STORYBOARD = GenerationTaskRepository.TASK_TYPE_EXTERNAL_VIDEO_STORYBOARD;

    /** GET /jobs 可命中的文本类 task_type（本波四类；不含 old/V1/storyboard-v2）。 */
    public static final Set<String> TEXT_TASK_TYPES =
            Set.of(WORK_ORDER_ANALYSIS_V3, TOPIC_SELECTION, COPY_GENERATION, VIDEO_STORYBOARD);

    private ExternalAiTaskTypes() {}

    public static boolean isTextTaskType(String taskType) {
        return taskType != null && TEXT_TASK_TYPES.contains(taskType);
    }
}

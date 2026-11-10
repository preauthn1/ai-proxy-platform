package com.yunpei.proxy.service.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.easy.query.core.api.pagination.EasyPageResult;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.service.entity.TemporalTaskExecution;
import com.yunpei.proxy.service.repository.TemporalTaskConfigRepository;
import com.yunpei.proxy.service.repository.TemporalTaskExecutionRepository;
import com.yunpei.proxy.service.task.TaskManageModels.TaskExecutionListQuery;
import com.yunpei.proxy.service.task.TaskManageModels.TaskExecutionView;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link TaskExecutionService}：configId 为 null 的直启执行记录不应 NPE。
 */
class TaskExecutionServiceTest {

    @Test
    void page_allowsNullConfigIdWithoutNpe() {
        TemporalTaskExecutionRepository executionRepository = mock(TemporalTaskExecutionRepository.class);
        TemporalTaskConfigRepository configRepository = mock(TemporalTaskConfigRepository.class);

        TemporalTaskExecution row = new TemporalTaskExecution();
        row.setId(1L);
        row.setConfigId(null); // 视频/图片直启种子
        row.setWorkflowId("wf-video_generation-x");
        row.setWorkflowType(TemporalWorkflowType.VIDEO_GENERATION);
        row.setStatus("PENDING");
        row.setRetryCount(0);

        @SuppressWarnings("unchecked")
        EasyPageResult<TemporalTaskExecution> page = mock(EasyPageResult.class);
        when(page.getData()).thenReturn(List.of(row));
        when(page.getTotal()).thenReturn(1L);
        when(executionRepository.page(anyInt(), anyInt(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(page);
        when(configRepository.mapNameByIds(any())).thenReturn(Map.of());

        TaskExecutionService service = new TaskExecutionService(executionRepository, configRepository);
        PageData<TaskExecutionView> result =
                service.page(TaskExecutionListQuery.of(1, 20, null, null, null, null, null));

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).configId()).isNull();
        assertThat(result.getItems().get(0).configName()).isNull();
        assertThat(result.getItems().get(0).workflowType()).isEqualTo(TemporalWorkflowType.VIDEO_GENERATION);
    }
}

package com.yunpei.proxy.infra.temporal.workflow.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.service.externalai.ExternalAiGenerationService;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link AiTextGenerationActivityImpl} 委托测试（Seam S5：Activity → service.execute）。
 */
class AiTextGenerationActivityImplTest {

    @Test
    void execute_delegatesToService() {
        ExternalAiGenerationService service = mock(ExternalAiGenerationService.class);
        when(service.execute("job-9")).thenReturn(Map.of("request_id", "job-9", "status", "succeeded"));
        AiTextGenerationActivityImpl activity = new AiTextGenerationActivityImpl(service);

        Map<String, Object> result = activity.execute("job-9");

        assertThat(result).containsEntry("request_id", "job-9");
        verify(service).execute("job-9");
    }
}

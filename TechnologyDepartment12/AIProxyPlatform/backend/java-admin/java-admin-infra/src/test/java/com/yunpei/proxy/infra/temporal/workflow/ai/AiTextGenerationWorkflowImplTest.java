package com.yunpei.proxy.infra.temporal.workflow.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link AiTextGenerationWorkflowImpl#requireJobId} 入参契约测试（Seam S5）。
 */
class AiTextGenerationWorkflowImplTest {

    @Test
    void requireJobId_readsFromMap() {
        assertThat(AiTextGenerationWorkflowImpl.requireJobId(Map.of("jobId", "abc")))
                .isEqualTo("abc");
        assertThat(AiTextGenerationWorkflowImpl.requireJobId(Map.of("jobId", "  xyz  ")))
                .isEqualTo("xyz");
    }

    @Test
    void requireJobId_rejectsMissingOrBlank() {
        assertThatThrownBy(() -> AiTextGenerationWorkflowImpl.requireJobId(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AiTextGenerationWorkflowImpl.requireJobId(Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AiTextGenerationWorkflowImpl.requireJobId(Map.of("other", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jobId");
        assertThatThrownBy(() -> AiTextGenerationWorkflowImpl.requireJobId(Map.of("jobId", "  ")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

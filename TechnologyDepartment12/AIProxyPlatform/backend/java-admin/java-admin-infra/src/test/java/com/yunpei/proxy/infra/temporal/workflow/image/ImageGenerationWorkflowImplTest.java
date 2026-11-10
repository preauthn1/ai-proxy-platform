package com.yunpei.proxy.infra.temporal.workflow.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link ImageGenerationWorkflowImpl#requireJobId} 入参契约测试。
 */
class ImageGenerationWorkflowImplTest {

    @Test
    void requireJobId_readsFromMap() {
        assertThat(ImageGenerationWorkflowImpl.requireJobId(Map.of("jobId", "abc")))
                .isEqualTo("abc");
        assertThat(ImageGenerationWorkflowImpl.requireJobId(Map.of("jobId", "  xyz  ")))
                .isEqualTo("xyz");
    }

    @Test
    void requireJobId_rejectsMissingOrBlank() {
        assertThatThrownBy(() -> ImageGenerationWorkflowImpl.requireJobId(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImageGenerationWorkflowImpl.requireJobId(Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImageGenerationWorkflowImpl.requireJobId(Map.of("other", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jobId");
        assertThatThrownBy(() -> ImageGenerationWorkflowImpl.requireJobId(Map.of("jobId", "  ")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

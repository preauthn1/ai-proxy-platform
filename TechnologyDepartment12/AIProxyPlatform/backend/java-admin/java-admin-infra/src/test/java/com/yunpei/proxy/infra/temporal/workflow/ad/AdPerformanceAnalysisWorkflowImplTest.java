package com.yunpei.proxy.infra.temporal.workflow.ad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link AdPerformanceAnalysisWorkflowImpl#requireJobId} 入参契约。
 */
class AdPerformanceAnalysisWorkflowImplTest {

    @Test
    void requireJobId_readsFromMap() {
        assertThat(AdPerformanceAnalysisWorkflowImpl.requireJobId(Map.of("jobId", "abc")))
                .isEqualTo("abc");
        assertThat(AdPerformanceAnalysisWorkflowImpl.requireJobId(Map.of("jobId", "  xyz  ")))
                .isEqualTo("xyz");
    }

    @Test
    void requireJobId_rejectsMissingOrBlank() {
        assertThatThrownBy(() -> AdPerformanceAnalysisWorkflowImpl.requireJobId(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AdPerformanceAnalysisWorkflowImpl.requireJobId(Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AdPerformanceAnalysisWorkflowImpl.requireJobId(Map.of("jobId", "  ")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

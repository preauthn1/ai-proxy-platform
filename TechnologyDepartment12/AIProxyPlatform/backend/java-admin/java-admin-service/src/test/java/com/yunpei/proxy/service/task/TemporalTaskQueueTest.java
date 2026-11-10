package com.yunpei.proxy.service.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yunpei.proxy.common.exception.BizException;
import org.junit.jupiter.api.Test;

/**
 * {@link TemporalTaskQueue} 业务/系统队列边界单测。
 */
class TemporalTaskQueueTest {

    @Test
    void all_containsOnlyTaskConfigBusinessQueues() {
        assertThat(TemporalTaskQueue.ALL).containsExactly(TemporalTaskQueue.DEMO);
        assertThat(TemporalTaskQueue.ALL).doesNotContain(TemporalTaskQueue.SYSTEM, TemporalTaskQueue.IMAGE_GENERATION);
    }

    @Test
    void requireCode_acceptsDemo_rejectsSystemImageAndUnknown() {
        assertThat(TemporalTaskQueue.requireCode("demo")).isEqualTo(TemporalTaskQueue.DEMO);
        assertThat(TemporalTaskQueue.requireCode(" DEMO ")).isEqualTo(TemporalTaskQueue.DEMO);

        assertThatThrownBy(() -> TemporalTaskQueue.requireCode(TemporalTaskQueue.SYSTEM))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("unknown taskQueue");
        assertThatThrownBy(() -> TemporalTaskQueue.requireCode(TemporalTaskQueue.IMAGE_GENERATION))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("unknown taskQueue");
        assertThatThrownBy(() -> TemporalTaskQueue.requireCode("reports"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("unknown taskQueue");
    }

    @Test
    void isKnown_onlyTaskConfigBusinessQueues() {
        assertThat(TemporalTaskQueue.isKnown("demo")).isTrue();
        assertThat(TemporalTaskQueue.isKnown(TemporalTaskQueue.SYSTEM)).isFalse();
        assertThat(TemporalTaskQueue.isKnown(TemporalTaskQueue.IMAGE_GENERATION))
                .isFalse();
        assertThat(TemporalTaskQueue.isKnown(null)).isFalse();
    }

    @Test
    void imageGeneration_constantMatchesWorkerQueue() {
        assertThat(TemporalTaskQueue.IMAGE_GENERATION).isEqualTo("image-generation");
    }

    @Test
    void aiTextGeneration_constantMatchesWorkerQueue() {
        assertThat(TemporalTaskQueue.AI_TEXT_GENERATION).isEqualTo("ai-text-generation");
        assertThat(TemporalTaskQueue.ALL).doesNotContain(TemporalTaskQueue.AI_TEXT_GENERATION);
        assertThat(TemporalTaskQueue.isKnown(TemporalTaskQueue.AI_TEXT_GENERATION))
                .isFalse();
    }

    @Test
    void adPerformanceAnalysis_constantMatchesWorkerQueue() {
        assertThat(TemporalTaskQueue.AD_PERFORMANCE_ANALYSIS).isEqualTo("ad-performance-analysis");
        assertThat(TemporalTaskQueue.ALL).doesNotContain(TemporalTaskQueue.AD_PERFORMANCE_ANALYSIS);
        assertThat(TemporalTaskQueue.isKnown(TemporalTaskQueue.AD_PERFORMANCE_ANALYSIS))
                .isFalse();
    }
}

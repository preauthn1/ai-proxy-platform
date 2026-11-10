package com.yunpei.proxy.service.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yunpei.proxy.common.exception.BizException;
import org.junit.jupiter.api.Test;

/**
 * {@link TemporalWorkflowType} 任务配置门禁与镜像扫描列表边界单测。
 */
class TemporalWorkflowTypeTest {

    @Test
    void all_containsOnlyTaskConfigBusinessTypes() {
        assertThat(TemporalWorkflowType.ALL).containsExactly(TemporalWorkflowType.LOG_COUNT_TICK);
        assertThat(TemporalWorkflowType.ALL)
                .doesNotContain(TemporalWorkflowType.IMAGE_GENERATION, TemporalWorkflowType.EXECUTION_MIRROR_TICK);
    }

    @Test
    void forMirror_includesDirectStartWorkflowsAndExcludesSystem() {
        assertThat(TemporalWorkflowType.FOR_MIRROR)
                .containsExactly(
                        TemporalWorkflowType.LOG_COUNT_TICK,
                        TemporalWorkflowType.IMAGE_GENERATION,
                        TemporalWorkflowType.VIDEO_GENERATION,
                        TemporalWorkflowType.AI_TEXT_GENERATION,
                        TemporalWorkflowType.AD_PERFORMANCE_ANALYSIS);
        assertThat(TemporalWorkflowType.FOR_MIRROR).containsAll(TemporalWorkflowType.ALL);
        assertThat(TemporalWorkflowType.FOR_MIRROR).doesNotContain(TemporalWorkflowType.EXECUTION_MIRROR_TICK);
    }

    @Test
    void requireCode_acceptsLogCount_rejectsDirectStartAndSystem() {
        assertThat(TemporalWorkflowType.requireCode("LogCountTickWorkflow"))
                .isEqualTo(TemporalWorkflowType.LOG_COUNT_TICK);
        assertThatThrownBy(() -> TemporalWorkflowType.requireCode(TemporalWorkflowType.IMAGE_GENERATION))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("unknown workflowType");
        assertThatThrownBy(() -> TemporalWorkflowType.requireCode(TemporalWorkflowType.VIDEO_GENERATION))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("unknown workflowType");
        assertThatThrownBy(() -> TemporalWorkflowType.requireCode(TemporalWorkflowType.AI_TEXT_GENERATION))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("unknown workflowType");
        assertThatThrownBy(() -> TemporalWorkflowType.requireCode(TemporalWorkflowType.AD_PERFORMANCE_ANALYSIS))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("unknown workflowType");
        assertThatThrownBy(() -> TemporalWorkflowType.requireCode(TemporalWorkflowType.EXECUTION_MIRROR_TICK))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("unknown workflowType");
    }

    @Test
    void isKnown_onlyTaskConfigBusinessTypes() {
        assertThat(TemporalWorkflowType.isKnown(TemporalWorkflowType.LOG_COUNT_TICK))
                .isTrue();
        assertThat(TemporalWorkflowType.isKnown(TemporalWorkflowType.IMAGE_GENERATION))
                .isFalse();
        assertThat(TemporalWorkflowType.isKnown(TemporalWorkflowType.AI_TEXT_GENERATION))
                .isFalse();
        assertThat(TemporalWorkflowType.isKnown(TemporalWorkflowType.EXECUTION_MIRROR_TICK))
                .isFalse();
        assertThat(TemporalWorkflowType.isKnown(null)).isFalse();
    }
}

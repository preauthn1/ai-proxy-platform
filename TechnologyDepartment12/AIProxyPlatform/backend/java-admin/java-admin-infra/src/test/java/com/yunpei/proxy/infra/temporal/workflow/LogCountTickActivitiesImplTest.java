package com.yunpei.proxy.infra.temporal.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link LogCountTickActivitiesImpl} 计数逻辑单测。
 */
class LogCountTickActivitiesImplTest {

    private final LogCountTickActivitiesImpl activities = new LogCountTickActivitiesImpl();

    @BeforeEach
    void setUp() {
        LogCountTickActivitiesImpl.resetForTest();
    }

    @Test
    void incrementAndLog_increasesCountAndReturnsBusinessMap() {
        Map<String, Object> first = activities.incrementAndLog(Map.of("trigger", "manual"));
        assertThat(LogCountTickActivitiesImpl.COUNT.get()).isEqualTo(1L);
        assertThat(first.get("count")).isEqualTo(1L);
        assertThat(first.get("message")).isEqualTo("log_count_tick ok");
        assertThat(first.get("input")).isEqualTo(Map.of("trigger", "manual"));

        Map<String, Object> second = activities.incrementAndLog(null);
        assertThat(LogCountTickActivitiesImpl.COUNT.get()).isEqualTo(2L);
        assertThat(second.get("count")).isEqualTo(2L);
        assertThat(second.get("message")).isEqualTo("log_count_tick ok");
        assertThat(second.get("input")).isEqualTo(Map.of());
    }
}

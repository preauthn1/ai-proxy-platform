package com.yunpei.proxy.infra.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * {@link TimeProperties} 只允许 Asia/Shanghai。
 */
class TimePropertiesTest {

    @Test
    void defaultZone_isShanghai() {
        TimeProperties props = new TimeProperties();
        props.validate();
        assertThat(props.getZone()).isEqualTo("Asia/Shanghai");
    }

    @Test
    void otherZone_fails() {
        TimeProperties props = new TimeProperties();
        props.setZone("UTC");
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Asia/Shanghai");
    }
}

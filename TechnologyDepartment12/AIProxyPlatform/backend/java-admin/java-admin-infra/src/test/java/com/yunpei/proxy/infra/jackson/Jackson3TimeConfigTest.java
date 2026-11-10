package com.yunpei.proxy.infra.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 3 HTTP：LocalDateTime 带 +08:00。
 */
class Jackson3TimeConfigTest {

    private final JsonMapper mapper = buildMapper();

    private static JsonMapper buildMapper() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new Jackson3TimeConfig().platformLocalDateTimeCustomizer().customize(builder);
        return builder.build();
    }

    @Test
    void localDateTime_writesOffset() {
        String json = mapper.writeValueAsString(LocalDateTime.of(2026, 8, 14, 16, 0, 0));
        assertThat(json).isEqualTo("\"2026-08-14T16:00:00+08:00\"");
    }

    @Test
    void localDateTime_readsZuluAndOffset() {
        assertThat(mapper.readValue("\"2026-08-14T08:00:00Z\"", LocalDateTime.class))
                .isEqualTo(LocalDateTime.of(2026, 8, 14, 16, 0, 0));
        assertThat(mapper.readValue("\"2026-08-14T16:00:00+08:00\"", LocalDateTime.class))
                .isEqualTo(LocalDateTime.of(2026, 8, 14, 16, 0, 0));
    }
}

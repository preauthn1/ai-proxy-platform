package com.yunpei.proxy.infra.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * Jackson 2 ObjectMapper：LocalDateTime 带 +08:00。
 */
class JacksonConfigTest {

    private final ObjectMapper mapper = new JacksonConfig().objectMapper();

    @Test
    void localDateTime_writesOffset() throws Exception {
        String json = mapper.writeValueAsString(LocalDateTime.of(2026, 8, 14, 16, 0, 0));
        assertThat(json).isEqualTo("\"2026-08-14T16:00:00+08:00\"");
    }

    @Test
    void localDateTime_readsZuluAndNaive() throws Exception {
        assertThat(mapper.readValue("\"2026-08-14T08:00:00Z\"", LocalDateTime.class))
                .isEqualTo(LocalDateTime.of(2026, 8, 14, 16, 0, 0));
        assertThat(mapper.readValue("\"2026-08-14T16:00:00\"", LocalDateTime.class))
                .isEqualTo(LocalDateTime.of(2026, 8, 14, 16, 0, 0));
    }
}

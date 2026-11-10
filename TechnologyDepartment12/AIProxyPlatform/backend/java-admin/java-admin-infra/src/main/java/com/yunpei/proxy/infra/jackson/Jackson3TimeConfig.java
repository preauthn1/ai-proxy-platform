package com.yunpei.proxy.infra.jackson;

import com.yunpei.proxy.common.time.DateTimes;
import java.time.LocalDateTime;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Spring Boot 4 HTTP 层 Jackson 3：LocalDateTime 写出带 +08:00。
 *
 * @author yunpei
 */
@Configuration(proxyBeanMethods = false)
public final class Jackson3TimeConfig {

    /**
     * 覆盖默认 LocalDateTime 编解码。
     *
     * @return customizer
     */
    @Bean
    public JsonMapperBuilderCustomizer platformLocalDateTimeCustomizer() {
        SimpleModule module = new SimpleModule("platform-local-datetime");
        module.addSerializer(LocalDateTime.class, new PlatformLocalDateTimeSerializer());
        module.addDeserializer(LocalDateTime.class, new PlatformLocalDateTimeDeserializer());
        return builder -> builder.addModule(module);
    }

    static final class PlatformLocalDateTimeSerializer extends ValueSerializer<LocalDateTime> {

        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializationContext ctxt) {
            gen.writeString(DateTimes.formatOffset(value));
        }
    }

    static final class PlatformLocalDateTimeDeserializer extends ValueDeserializer<LocalDateTime> {

        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) {
            return DateTimes.parse(p.getValueAsString());
        }
    }
}

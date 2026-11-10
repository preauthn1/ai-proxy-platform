package com.yunpei.proxy.infra.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yunpei.proxy.common.time.DateTimes;
import java.io.IOException;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 2 {@link ObjectMapper} 全局 Bean（日志切面等）。
 *
 * <p>HTTP 层是 Jackson 3，见 {@link Jackson3TimeConfig}。
 *
 * @author yunpei
 */
@Configuration(proxyBeanMethods = false)
public final class JacksonConfig {

    /**
     * 提供全局 ObjectMapper：Java Time、未知字段不失败、LocalDateTime 带 +08:00。
     *
     * @return 配置完成的 ObjectMapper
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(platformLocalDateTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }

    static SimpleModule platformLocalDateTimeModule() {
        SimpleModule module = new SimpleModule("platform-local-datetime");
        module.addSerializer(LocalDateTime.class, new JsonSerializer<>() {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                gen.writeString(DateTimes.formatOffset(value));
            }
        });
        module.addDeserializer(LocalDateTime.class, new JsonDeserializer<>() {
            @Override
            public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                return DateTimes.parse(p.getValueAsString());
            }
        });
        return module;
    }
}

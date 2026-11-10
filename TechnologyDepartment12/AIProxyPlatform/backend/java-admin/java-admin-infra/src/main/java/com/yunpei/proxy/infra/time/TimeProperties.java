package com.yunpei.proxy.infra.time;

import com.yunpei.proxy.common.time.TimeZones;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 平台时钟配置。
 *
 * <p>对应 {@code app.time.*}。当前只允许 {@link TimeZones#PLATFORM_ID}。
 *
 * @author yunpei
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.time")
public class TimeProperties {

    /** 平台墙钟 IANA；仅支持 Asia/Shanghai。 */
    private String zone = TimeZones.PLATFORM_ID;

    @PostConstruct
    void validate() {
        if (!TimeZones.PLATFORM_ID.equals(zone)) {
            throw new IllegalStateException("app.time.zone 仅支持 " + TimeZones.PLATFORM_ID + "，实际=" + zone);
        }
    }
}

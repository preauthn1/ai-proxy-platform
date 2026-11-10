package com.yunpei.proxy.service.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ALTCHA 人机校验配置属性。
 *
 * <p>对应 {@code application.yml} 中的 {@code altcha.*} 配置项。
 *
 * @author yunpei
 */
@Data
@Component
@ConfigurationProperties(prefix = "altcha")
public class AltchaProperties {

    /** HMAC 密钥；与 mock 默认一致，便于本地联调；生产通过配置注入。 */
    private String hmacSecret = "altcha-dev-hmac-secret";

    /** PoW 成本；dev 取 1000，浏览器端秒级可解。 */
    private int cost = 1000;

    /** 挑战有效期（秒）。 */
    private long expiresSeconds = 600;
}

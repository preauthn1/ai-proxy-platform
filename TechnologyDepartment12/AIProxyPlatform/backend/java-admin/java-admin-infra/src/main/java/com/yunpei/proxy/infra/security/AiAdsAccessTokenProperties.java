package com.yunpei.proxy.infra.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 外部集成共享密钥配置（对应 {@code app.ai-ads.access-token}）。
 *
 * <p>由 {@link AiAdsAccessTokenFilter} 使用，对 image/video 外部集成路径做常量时间比较；
 * 配置为空时放行（对齐 Python 语义）。
 *
 * @author yunpei
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.ai-ads")
public class AiAdsAccessTokenProperties {

    /** 共享访问令牌；空 → 鉴权放行。 */
    private String accessToken;
}

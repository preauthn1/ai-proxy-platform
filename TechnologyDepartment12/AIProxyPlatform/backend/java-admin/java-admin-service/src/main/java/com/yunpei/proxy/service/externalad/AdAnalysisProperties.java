package com.yunpei.proxy.service.externalad;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 广告效果分析集成配置（对应 {@code app.ad-analysis.*}）。
 *
 * @author yunpei
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.ad-analysis")
public class AdAnalysisProperties {

    /**
     * 对外 poll_url 的公网站点根（不含尾斜杠）。为空则返回相对路径。
     */
    private String publicBaseUrl = "";

    public String pollUrl(String analysisId) {
        String path = "/api/v1/integrations/ad-performance/analysis-jobs/" + analysisId;
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return path;
        }
        return publicBaseUrl.replaceAll("/+$", "") + path;
    }
}

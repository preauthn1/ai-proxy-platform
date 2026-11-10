package com.yunpei.proxy.service.externalad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yunpei.proxy.common.exception.BizException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link AdAnalysisRequestValidator} 契约：媒体 URL、btn_type、adset 枚举。
 */
class AdAnalysisRequestValidatorTest {

    @Test
    void validate_normalizesBtnTypeAndInsightStatus() {
        Map<String, Object> payload = sample();
        payload.put(
                "creative",
                new LinkedHashMap<>(Map.of(
                        "creative_type", "image", "image_url", "https://cdn.example.com/a.jpg", "btn_type", "了解更多")));
        payload.put("insight", new LinkedHashMap<>(Map.of("status", "active", "spend", "0.24")));

        AdAnalysisRequestValidator.validate(payload);

        @SuppressWarnings("unchecked")
        Map<String, Object> creative = (Map<String, Object>) payload.get("creative");
        @SuppressWarnings("unchecked")
        Map<String, Object> insight = (Map<String, Object>) payload.get("insight");
        assertThat(creative.get("btn_type")).isEqualTo("LEARN_MORE");
        assertThat(insight.get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void validate_rejectsMissingImageUrl() {
        Map<String, Object> payload = sample();
        payload.put("creative", Map.of("creative_type", "image"));
        assertThatThrownBy(() -> AdAnalysisRequestValidator.validate(payload))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("creative.image_url is required");
    }

    @Test
    void validate_rejectsCarouselTooFewImages() {
        Map<String, Object> payload = sample();
        payload.put("creative", Map.of("creative_type", "carousel", "image_urls", List.of("https://a.example/x.jpg")));
        assertThatThrownBy(() -> AdAnalysisRequestValidator.validate(payload))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("between 2 and 10");
    }

    @Test
    void validate_rejectsBlankExternalRequestId() {
        Map<String, Object> payload = sample();
        payload.put("external_request_id", "   ");
        assertThatThrownBy(() -> AdAnalysisRequestValidator.validate(payload))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("external_request_id");
    }

    @Test
    void validate_rejectsAdsetBidStrategyWithoutBudget() {
        Map<String, Object> payload = sample();
        payload.put("adset", Map.of("bid_strategy", "LOWEST_COST_WITHOUT_CAP"));
        assertThatThrownBy(() -> AdAnalysisRequestValidator.validate(payload))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("daily_budget");
    }

    static Map<String, Object> sample() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("external_request_id", "job-1");
        payload.put("source_type", "external");
        payload.put("campaign", new LinkedHashMap<>(Map.of("name", "new1")));
        payload.put("adset", new LinkedHashMap<>(Map.of("optimization_goal", "LINK_CLICKS")));
        payload.put(
                "creative",
                new LinkedHashMap<>(
                        Map.of("creative_type", "image", "image_url", "https://newpixel.messrocts.com/uploads/a.jpg")));
        payload.put("insight", new LinkedHashMap<>(Map.of("status", "ACTIVE", "spend", "0.24")));
        payload.put("siblings", new ArrayList<>());
        return payload;
    }
}

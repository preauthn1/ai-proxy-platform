package com.yunpei.proxy.service.externalad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * payload hash：忽略 thumbnail/keyframes；数值规范化后 spend 变化导致不同 hash。
 */
class AdAnalysisPayloadCanonicalizerTest {

    @Test
    void hash_ignoresThumbnailAndKeyframes() {
        Map<String, Object> base = AdAnalysisRequestValidatorTest.sample();
        AdAnalysisRequestValidator.validate(base);
        String hash = AdAnalysisPayloadCanonicalizer.payloadHash(base);

        Map<String, Object> withMedia = AdAnalysisRequestValidatorTest.sample();
        @SuppressWarnings("unchecked")
        Map<String, Object> creative = (Map<String, Object>) withMedia.get("creative");
        creative.put("thumbnail_url", "https://cdn.example.com/thumb.jpg");
        creative.put("video_keyframes", Map.of("ignored", true));
        AdAnalysisRequestValidator.validate(withMedia);

        assertThat(AdAnalysisPayloadCanonicalizer.payloadHash(withMedia)).isEqualTo(hash);
    }

    @Test
    void hash_changesWhenSpendChanges() {
        Map<String, Object> first = AdAnalysisRequestValidatorTest.sample();
        AdAnalysisRequestValidator.validate(first);
        Map<String, Object> second = AdAnalysisRequestValidatorTest.sample();
        @SuppressWarnings("unchecked")
        Map<String, Object> insight = (Map<String, Object>) second.get("insight");
        insight.put("spend", "9.99");
        AdAnalysisRequestValidator.validate(second);

        assertThat(AdAnalysisPayloadCanonicalizer.payloadHash(first))
                .isNotEqualTo(AdAnalysisPayloadCanonicalizer.payloadHash(second));
    }

    @Test
    void canonicalize_dropsExternalRequestId() {
        Map<String, Object> payload = AdAnalysisRequestValidatorTest.sample();
        AdAnalysisRequestValidator.validate(payload);
        Map<String, Object> canonical = AdAnalysisPayloadCanonicalizer.canonicalize(payload);
        assertThat(canonical).doesNotContainKey("external_request_id");
        assertThat(canonical.get("source_type")).isEqualTo("external");
    }

    @Test
    void hash_isStableAcrossMapKeyOrder() {
        Map<String, Object> a = AdAnalysisRequestValidatorTest.sample();
        AdAnalysisRequestValidator.validate(a);
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("insight", a.get("insight"));
        b.put("creative", a.get("creative"));
        b.put("adset", a.get("adset"));
        b.put("campaign", a.get("campaign"));
        b.put("siblings", a.get("siblings"));
        b.put("source_type", a.get("source_type"));
        b.put("external_request_id", a.get("external_request_id"));
        assertThat(AdAnalysisPayloadCanonicalizer.payloadHash(b))
                .isEqualTo(AdAnalysisPayloadCanonicalizer.payloadHash(a));
    }
}

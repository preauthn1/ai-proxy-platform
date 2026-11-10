package com.yunpei.proxy.service.externalai.workorder.v3;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 广告目标检测结果。
 *
 * @param detectedObjective Meta objective（如 OUTCOME_SALES）
 * @param matchedRule 命中规则名
 * @param matchedValue 命中证据
 * @author yunpei
 */
public record ObjectiveDetection(String detectedObjective, String matchedRule, String matchedValue) {

    public Map<String, String> objectiveDetectionPayload() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("matched_rule", matchedRule);
        map.put("matched_value", matchedValue);
        return map;
    }
}

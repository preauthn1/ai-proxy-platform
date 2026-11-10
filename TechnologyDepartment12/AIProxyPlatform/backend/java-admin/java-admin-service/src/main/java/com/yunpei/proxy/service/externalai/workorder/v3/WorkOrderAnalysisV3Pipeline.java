package com.yunpei.proxy.service.externalai.workorder.v3;

import static com.yunpei.proxy.service.externalai.workorder.v3.WorkOrderAnalysisV3Constants.RULE_CONVERSION;
import static com.yunpei.proxy.service.externalai.workorder.v3.WorkOrderAnalysisV3Constants.RULE_LLM_FALLBACK;

import com.yunpei.proxy.service.externalai.workorder.CustomEventTypes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V3 工单分析纯管线：目标检测 → 字段合并 → payload 组装（无 I/O）。
 *
 * <p>对齐 Python {@code analyze_work_order_v3}。
 *
 * @author yunpei
 */
public final class WorkOrderAnalysisV3Pipeline {

    private WorkOrderAnalysisV3Pipeline() {}

    /**
     * 执行规则优先分析。
     *
     * @param workOrderText 工单正文
     * @param workOrderType 工单类型（预留）
     * @param media 媒介
     * @param requestId 结果 request_id
     * @param llmFields LLM 投递字段（可选）
     * @param llmHints LLM 语义补洞（可选；仅填规则空缺）
     * @return V3 业务载荷
     */
    public static Map<String, Object> analyze(
            String workOrderText,
            String workOrderType,
            String media,
            String requestId,
            Map<String, Object> llmFields,
            Map<String, Object> llmHints) {
        // workOrderType reserved for future creative/compliance hooks
        ObjectiveDetection ruleDetection = ObjectiveDetector.detectObjective(workOrderText);
        String ruleMatched = ruleDetection.matchedRule();
        ObjectiveDetection detection = ObjectiveDetector.applyLlmObjectiveHints(ruleDetection, llmHints);

        ExtractedFields fields = FieldExtract.mergeExtractedFields(workOrderText, llmFields, media);
        fields = FieldExtract.applyLlmRunHourHints(fields, llmHints);

        if (RULE_CONVERSION.equals(detection.matchedRule()) && isBlank(fields.getEventName())) {
            fields.setEventName(detection.matchedValue());
            if ("ROAS".equals(detection.matchedValue())) {
                fields.setEventName("ROAS");
            } else if ("PURCHASE".equals(detection.matchedValue())) {
                String mapped = CustomEventTypes.customEventType("PURCHASE");
                fields.setCustomEventType(mapped != null ? mapped : "PURCHASE");
            } else if ("REGISTER".equals(detection.matchedValue())) {
                fields.setCustomEventType("COMPLETE_REGISTRATION");
            } else if ("首充".equals(detection.matchedValue())) {
                fields.setCustomEventType("PURCHASE");
            }
        }

        Map<String, Object> result =
                PayloadBuilder.buildV3Result(requestId, workOrderText, detection, fields, llmHints);

        if (RULE_LLM_FALLBACK.equals(detection.matchedRule())) {
            @SuppressWarnings("unchecked")
            Map<String, Object> review =
                    (Map<String, Object>) result.computeIfAbsent("review", k -> new LinkedHashMap<>());
            review.put("requires_user_review", true);
            @SuppressWarnings("unchecked")
            Map<String, Object> decisions =
                    (Map<String, Object>) review.computeIfAbsent("field_decisions", k -> new LinkedHashMap<>());
            Map<String, Object> objective = new LinkedHashMap<>();
            objective.put("source", "llm_fallback");
            objective.put(
                    "reason",
                    "rules="
                            + ruleMatched
                            + "; llm filled objective="
                            + detection.detectedObjective()
                            + ", matched_value="
                            + detection.matchedValue());
            decisions.put("objective", objective);
            @SuppressWarnings("unchecked")
            List<String> warnings = (List<String>) review.computeIfAbsent("warnings", k -> new ArrayList<>());
            warnings.add("广告目标由 AI 兜底识别，请人工确认");
        }
        if (media != null && !media.isBlank() && !"fb".equalsIgnoreCase(media.strip())) {
            @SuppressWarnings("unchecked")
            Map<String, Object> review =
                    (Map<String, Object>) result.computeIfAbsent("review", k -> new LinkedHashMap<>());
            @SuppressWarnings("unchecked")
            List<String> warnings = (List<String>) review.computeIfAbsent("warnings", k -> new ArrayList<>());
            warnings.add("media is not fb; returned payload still uses Facebook field names.");
        }
        return result;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

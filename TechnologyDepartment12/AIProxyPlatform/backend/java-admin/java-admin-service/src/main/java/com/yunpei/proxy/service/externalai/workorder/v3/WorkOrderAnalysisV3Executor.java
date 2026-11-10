package com.yunpei.proxy.service.externalai.workorder.v3;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunpei.proxy.service.llm.LlmProvider;
import com.yunpei.proxy.service.llm.LlmRequest;
import com.yunpei.proxy.service.llm.LlmResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 工单分析 V3 执行编排：规则优先 + LLM 投递字段/补洞 best-effort。
 *
 * <p>对齐 Python {@code execute_work_order_analysis_v3}。
 *
 * @author yunpei
 */
public final class WorkOrderAnalysisV3Executor {

    private static final Logger log = LoggerFactory.getLogger(WorkOrderAnalysisV3Executor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    public static final String PURPOSE_DELIVERY_FIELDS = "work_order_delivery_fields";
    public static final String PURPOSE_ANALYSIS_HINTS = "work_order_analysis_hints";

    private WorkOrderAnalysisV3Executor() {}

    /**
     * 执行 V3 分析。
     *
     * @param llm LLM 端口（补洞/投递字段；失败不整单失败）
     * @param workOrderText 工单正文
     * @param workOrderType 工单类型
     * @param media 媒介
     * @param requestId 结果 request_id
     * @return V3 业务载荷
     */
    public static Map<String, Object> execute(
            LlmProvider llm, String workOrderText, String workOrderType, String media, String requestId) {
        Map<String, Object> llmFields = extractDeliveryFieldsBestEffort(llm, workOrderText);

        ObjectiveDetection ruleDetection = ObjectiveDetector.detectObjective(workOrderText);
        ExtractedFields ruleFields = FieldExtract.mergeExtractedFields(workOrderText, llmFields, media);

        Map<String, Object> llmHints = null;
        if (ObjectiveDetector.needsV3LlmFallback(
                workOrderText, ruleDetection, ruleFields.getRunStartTime(), ruleFields.getRunStopTime())) {
            llmHints = extractHintsBestEffort(llm, workOrderText);
        }

        return WorkOrderAnalysisV3Pipeline.analyze(workOrderText, workOrderType, media, requestId, llmFields, llmHints);
    }

    private static Map<String, Object> extractDeliveryFieldsBestEffort(LlmProvider llm, String workOrderText) {
        if (llm == null) {
            return Map.of();
        }
        try {
            LlmResponse response = llm.complete(LlmRequest.of(
                    "Extract structured ad delivery fields from the work order as JSON. "
                            + "Return {\"fields\":{...}} with optional keys: "
                            + "project_name, product_name, brand_name, landing_url, event_name, "
                            + "country, age_min, age_max, gender, audience, launch_time, "
                            + "run_start_time, run_stop_time. Values may be plain or "
                            + "{normalized_value,value}.",
                    "Work order:\n" + (workOrderText == null ? "" : workOrderText),
                    PURPOSE_DELIVERY_FIELDS));
            Map<String, Object> parsed = parseJson(response == null ? null : response.content());
            Object fields = parsed.get("fields");
            if (fields instanceof Map<?, ?> map) {
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    out.put(String.valueOf(e.getKey()), e.getValue());
                }
                return out;
            }
            // tolerate flat JSON field bag
            if (!parsed.isEmpty() && !parsed.containsKey("schema_version")) {
                return parsed;
            }
            return Map.of();
        } catch (RuntimeException ex) {
            log.warn("work-order delivery field extraction failed; continue rule-only: {}", ex.getMessage());
            return Map.of();
        }
    }

    private static Map<String, Object> extractHintsBestEffort(LlmProvider llm, String workOrderText) {
        if (llm == null) {
            return null;
        }
        try {
            LlmResponse response = llm.complete(LlmRequest.of(
                    "Fill residual V3 work-order analysis gaps as JSON: "
                            + "objective, confidence (0-1), matched_value, evidence, "
                            + "messaging{channel,whatsAppAreaCode,whatsAppNumber}, "
                            + "run_start_time, run_stop_time. Only fill when confident.",
                    "Work order:\n" + (workOrderText == null ? "" : workOrderText),
                    PURPOSE_ANALYSIS_HINTS));
            Map<String, Object> hints = parseJson(response == null ? null : response.content());
            return hints.isEmpty() ? null : hints;
        } catch (RuntimeException ex) {
            // AI fallback is best-effort; keep rule-only analysis on provider failure.
            log.warn("work-order analysis hints failed; continue rule-only: {}", ex.getMessage());
            return null;
        }
    }

    private static Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> map = MAPPER.readValue(json.strip(), MAP_TYPE);
            return map == null ? new LinkedHashMap<>() : new LinkedHashMap<>(map);
        } catch (Exception e) {
            // tolerate fenced content
            String trimmed = json.strip();
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    Map<String, Object> map = MAPPER.readValue(trimmed.substring(start, end + 1), MAP_TYPE);
                    return map == null ? new LinkedHashMap<>() : new LinkedHashMap<>(map);
                } catch (Exception ignore) {
                    return new LinkedHashMap<>();
                }
            }
            return new LinkedHashMap<>();
        }
    }
}

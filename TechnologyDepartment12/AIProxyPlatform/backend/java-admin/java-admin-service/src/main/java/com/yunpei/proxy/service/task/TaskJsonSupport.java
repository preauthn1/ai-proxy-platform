package com.yunpei.proxy.service.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.ResultCode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 任务模块 JSON 字段工具（retryPolicy / inputSummary / resultSummary）。
 *
 * <p>实体层存字符串，View 层用 Map；校验失败统一抛 {@link BizException}。
 *
 * @author yunpei
 */
public final class TaskJsonSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private TaskJsonSupport() {}

    /** 解析 JSON 对象字符串；blank → null。 */
    public static Map<String, Object> parseObject(String json, String field) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> map = MAPPER.readValue(json, MAP_TYPE);
            return map == null ? null : new LinkedHashMap<>(map);
        } catch (JsonProcessingException e) {
            throw BizException.of(ResultCode.PARAM_INVALID, field + " must be valid JSON object");
        }
    }

    /** Map / 已是 JSON 字符串 → 库内 JSON 文本；null/空 → null。 */
    public static String toJson(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            // 校验为对象
            parseObject(trimmed, field);
            return trimmed;
        }
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                return "{}";
            }
            try {
                return MAPPER.writeValueAsString(map);
            } catch (JsonProcessingException e) {
                throw BizException.of(ResultCode.PARAM_INVALID, field + " must be a JSON object");
            }
        }
        throw BizException.of(ResultCode.PARAM_INVALID, field + " must be a JSON object");
    }

    /** 规范化入参 Map 为可序列化副本；null → null。 */
    public static Map<String, Object> copyMap(Map<String, Object> source) {
        if (source == null) {
            return null;
        }
        return new LinkedHashMap<>(source);
    }
}

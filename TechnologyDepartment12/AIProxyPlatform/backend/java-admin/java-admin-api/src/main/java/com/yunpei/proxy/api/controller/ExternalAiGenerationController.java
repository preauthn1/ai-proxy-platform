package com.yunpei.proxy.api.controller;

import com.yunpei.proxy.api.dto.ExternalAiCopyGenerationCreateRequest;
import com.yunpei.proxy.api.dto.ExternalAiWorkOrderAnalysisCreateRequest;
import com.yunpei.proxy.api.vo.ExternalAiGenerationEnvelope;
import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.service.externalai.AiGenerationJobView;
import com.yunpei.proxy.service.externalai.AiGenerationTaskNotFoundException;
import com.yunpei.proxy.service.externalai.CreateAiJobCommand;
import com.yunpei.proxy.service.externalai.CreateAiJobResult;
import com.yunpei.proxy.service.externalai.ExternalAiGenerationService;
import com.yunpei.proxy.service.externalai.ExternalAiTaskTypes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 外部 AI 文本生成集成端点（对齐 Python {@code /integrations/ai}）。
 *
 * <p>返回<strong>外部信封</strong>；鉴权由 {@code AiAdsAccessTokenFilter} 完成。
 * 创建 202 data 含 {@code asy_task_id}/{@code job_id}/{@code status}/{@code retry_after_seconds}。
 *
 * @author yunpei
 */
@Tag(name = "AI 文本生成集成", description = "外部共享密钥鉴权；外部信封 snake_case")
@RestController
@RequestMapping("/api/v1/integrations/ai")
@RequiredArgsConstructor
public class ExternalAiGenerationController {

    public static final int CODE_SUCCESS = 0;
    public static final int CODE_PROCESSING = 1001;
    public static final int CODE_VALIDATION_ERROR = 4001;
    public static final int CODE_PROVIDER_ERROR = 5001;
    public static final int RETRY_AFTER_SECONDS = 2;

    private final ExternalAiGenerationService service;

    @PostMapping("/work-order-analysis")
    @Operation(summary = "创建工单分析任务（V3）", description = "落库 processing + 触发 Temporal；返回 202")
    public ResponseEntity<ExternalAiGenerationEnvelope> createWorkOrderAnalysis(
            @RequestBody ExternalAiWorkOrderAnalysisCreateRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("external_request_id", request.getExternalRequestId());
        payload.put("language", request.getLanguage());
        payload.put("work_order_type", request.getWorkOrderType());
        payload.put("work_order_text", request.getWorkOrderText());
        payload.put("media", request.getMedia());
        CreateAiJobResult result = service.createJob(CreateAiJobCommand.of(
                ExternalAiTaskTypes.WORK_ORDER_ANALYSIS_V3, request.getExternalRequestId(), payload));
        return accepted(result);
    }

    @PostMapping("/copy")
    @Operation(summary = "创建文案任务", description = "落库 processing + 触发 Temporal；返回 202。无独立选题接口；文案链路内部优化提示词与创意角度")
    public ResponseEntity<ExternalAiGenerationEnvelope> createCopy(
            @RequestBody ExternalAiCopyGenerationCreateRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("external_request_id", request.getExternalRequestId());
        payload.put("language", request.getLanguage());
        payload.put("product_name", request.getProductName());
        payload.put("landing_url", request.getLandingUrl());
        payload.put("audience", request.getAudience());
        payload.put("country", request.getCountry());
        payload.put("event_name", request.getEventName());
        payload.put("customEventType", request.getCustomEventType());
        payload.put("brief", request.getBrief());
        payload.put("selling_points", request.getSellingPoints());
        payload.put("constraints", request.getConstraints());
        payload.put("campaign", request.getCampaign());
        payload.put("adset", request.getAdset());
        payload.put("creative", request.getCreative());
        payload.put("count", request.getCount());
        CreateAiJobResult result = service.createJob(
                CreateAiJobCommand.of(ExternalAiTaskTypes.COPY_GENERATION, request.getExternalRequestId(), payload));
        return accepted(result);
    }

    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "查询 AI 文本任务", description = "只读库；仅命中文本类 task_type；succeeded 展平 result")
    public ResponseEntity<ExternalAiGenerationEnvelope> get(@PathVariable String jobId) {
        AiGenerationJobView view = service.getJob(jobId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("asy_task_id", view.jobId());
        data.put("job_id", view.jobId());
        data.put("status", view.status());
        int code;
        String message;
        switch (view.status()) {
            case "succeeded" -> {
                if (view.result() != null) {
                    data.putAll(view.result());
                }
                // 保证身份字段不被 result 覆盖
                data.put("asy_task_id", view.jobId());
                data.put("job_id", view.jobId());
                data.put("status", "succeeded");
                code = CODE_SUCCESS;
                message = "success";
            }
            case "failed" -> {
                data.put("error", view.error());
                code = CODE_PROVIDER_ERROR;
                message = "ai generation failed";
            }
            default -> {
                data.put("retry_after_seconds", RETRY_AFTER_SECONDS);
                code = CODE_PROCESSING;
                message = "processing";
            }
        }
        return ResponseEntity.ok(new ExternalAiGenerationEnvelope(code, message, data));
    }

    private static ResponseEntity<ExternalAiGenerationEnvelope> accepted(CreateAiJobResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("asy_task_id", result.jobId());
        data.put("job_id", result.jobId());
        data.put("status", "processing");
        data.put("retry_after_seconds", RETRY_AFTER_SECONDS);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new ExternalAiGenerationEnvelope(CODE_PROCESSING, "processing", data));
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ExternalAiGenerationEnvelope> handleBiz(BizException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ExternalAiGenerationEnvelope(
                        CODE_VALIDATION_ERROR, ex.getMessage(), Map.of("errors", List.of(ex.getMessage()))));
    }

    @ExceptionHandler(AiGenerationTaskNotFoundException.class)
    public ResponseEntity<ExternalAiGenerationEnvelope> handleNotFound(AiGenerationTaskNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ExternalAiGenerationEnvelope(CODE_VALIDATION_ERROR, ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ExternalAiGenerationEnvelope> handleNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ExternalAiGenerationEnvelope(CODE_VALIDATION_ERROR, "request body is invalid", Map.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExternalAiGenerationEnvelope> handleAny(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ExternalAiGenerationEnvelope(CODE_PROVIDER_ERROR, "ai generation failed", Map.of()));
    }
}

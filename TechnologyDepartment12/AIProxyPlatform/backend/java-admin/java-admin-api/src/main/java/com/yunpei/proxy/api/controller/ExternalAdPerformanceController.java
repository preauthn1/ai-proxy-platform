package com.yunpei.proxy.api.controller;

import com.yunpei.proxy.api.dto.ExternalAdPerformanceAnalysisCreateRequest;
import com.yunpei.proxy.api.vo.AdAnalysisCreateDataVO;
import com.yunpei.proxy.api.vo.AdAnalysisJobDataVO;
import com.yunpei.proxy.api.vo.AdAnalysisJobErrorVO;
import com.yunpei.proxy.api.vo.ExternalAdPerformanceEnvelope;
import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.service.externalad.AdAnalysisDispatchException;
import com.yunpei.proxy.service.externalad.AdAnalysisIdempotencyConflict;
import com.yunpei.proxy.service.externalad.AdAnalysisJobView;
import com.yunpei.proxy.service.externalad.AdAnalysisNotFoundException;
import com.yunpei.proxy.service.externalad.CreateAdAnalysisResult;
import com.yunpei.proxy.service.externalad.ExternalAdPerformanceAnalysisService;
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
 * 外部广告效果分析集成端点（对齐 Python {@code /integrations/ad-performance/analysis-jobs}）。
 *
 * <p>返回外部信封；鉴权由 {@code AiAdsAccessTokenFilter} 完成。
 *
 * @author yunpei
 */
@Tag(name = "广告效果分析集成", description = "外部共享密钥鉴权；外部信封 snake_case")
@RestController
@RequestMapping("/api/v1/integrations/ad-performance")
@RequiredArgsConstructor
public class ExternalAdPerformanceController {

    public static final int CODE_SUCCESS = 0;
    public static final int CODE_ACCEPTED = 1001;
    public static final int CODE_VALIDATION_ERROR = 4001;
    public static final int CODE_SERVER_ERROR = 5001;

    private final ExternalAdPerformanceAnalysisService service;

    @PostMapping("/analysis-jobs")
    @Operation(summary = "创建广告效果分析任务", description = "校验 + 幂等；新建 202/1001，回放 200/0，冲突 409/4001")
    public ResponseEntity<ExternalAdPerformanceEnvelope> create(
            @RequestBody ExternalAdPerformanceAnalysisCreateRequest request) {
        CreateAdAnalysisResult result = service.createJob(request.toPayload());
        AdAnalysisCreateDataVO data = new AdAnalysisCreateDataVO(
                result.analysisId(),
                result.externalRequestId(),
                result.status(),
                result.stage(),
                result.createdAt(),
                result.pollUrl(),
                result.idempotentReplay());
        if (result.idempotentReplay()) {
            return ResponseEntity.ok(new ExternalAdPerformanceEnvelope(CODE_SUCCESS, "ok", data));
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new ExternalAdPerformanceEnvelope(CODE_ACCEPTED, "accepted", data));
    }

    @GetMapping("/analysis-jobs/{analysisId}")
    @Operation(summary = "查询广告效果分析任务", description = "按 analysis_id 轮询；未找到 404/4001")
    public ResponseEntity<ExternalAdPerformanceEnvelope> get(@PathVariable String analysisId) {
        AdAnalysisJobView view = service.getJob(analysisId);
        AdAnalysisJobErrorVO error = view.error() == null
                ? null
                : new AdAnalysisJobErrorVO(
                        view.error().errorCode(),
                        view.error().message(),
                        view.error().retryable());
        AdAnalysisJobDataVO data = new AdAnalysisJobDataVO(
                view.analysisId(),
                view.externalRequestId(),
                view.status(),
                view.stage(),
                view.progress(),
                view.createdAt(),
                view.startedAt(),
                view.completedAt(),
                view.result(),
                error);
        return ResponseEntity.ok(new ExternalAdPerformanceEnvelope(CODE_SUCCESS, "ok", data));
    }

    @ExceptionHandler(AdAnalysisIdempotencyConflict.class)
    public ResponseEntity<ExternalAdPerformanceEnvelope> handleConflict(AdAnalysisIdempotencyConflict ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ExternalAdPerformanceEnvelope(CODE_VALIDATION_ERROR, ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(AdAnalysisNotFoundException.class)
    public ResponseEntity<ExternalAdPerformanceEnvelope> handleNotFound(AdAnalysisNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ExternalAdPerformanceEnvelope(CODE_VALIDATION_ERROR, ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(AdAnalysisDispatchException.class)
    public ResponseEntity<ExternalAdPerformanceEnvelope> handleDispatch(AdAnalysisDispatchException ex) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("analysis_id", ex.analysisId());
        data.put("external_request_id", ex.externalRequestId() == null ? "" : ex.externalRequestId());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ExternalAdPerformanceEnvelope(CODE_SERVER_ERROR, ex.getMessage(), data));
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ExternalAdPerformanceEnvelope> handleBiz(BizException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ExternalAdPerformanceEnvelope(
                        CODE_VALIDATION_ERROR, ex.getMessage(), Map.of("errors", List.of(ex.getMessage()))));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ExternalAdPerformanceEnvelope> handleNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ExternalAdPerformanceEnvelope(CODE_VALIDATION_ERROR, "request validation failed", Map.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExternalAdPerformanceEnvelope> handleAny(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ExternalAdPerformanceEnvelope(CODE_SERVER_ERROR, "analysis task dispatch failed", Map.of()));
    }
}

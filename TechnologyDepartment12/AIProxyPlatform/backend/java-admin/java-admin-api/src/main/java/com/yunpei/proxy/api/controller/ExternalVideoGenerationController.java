package com.yunpei.proxy.api.controller;

import com.yunpei.proxy.api.dto.ExternalVideoGenerationCreateRequest;
import com.yunpei.proxy.api.vo.ExternalVideoGenerationEnvelope;
import com.yunpei.proxy.api.vo.ExternalVideoGenerationJobVO;
import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.service.externalvideo.CreateVideoGenerationCommand;
import com.yunpei.proxy.service.externalvideo.CreateVideoJobResult;
import com.yunpei.proxy.service.externalvideo.ExternalVideoGenerationService;
import com.yunpei.proxy.service.externalvideo.VideoGenerationJobView;
import com.yunpei.proxy.service.externalvideo.VideoGenerationTaskNotFoundException;
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
 * 视频生成外部集成端点（对齐 Python {@code external_video_generation} + Java 图片集成模式）。
 *
 * <p>返回<strong>外部信封</strong>；鉴权由 {@code AiAdsAccessTokenFilter} 完成。
 *
 * @author yunpei
 */
@Tag(name = "视频生成集成", description = "外部共享密钥鉴权；外部信封 snake_case")
@RestController
@RequestMapping("/api/v1/integrations/video-generation")
@RequiredArgsConstructor
public class ExternalVideoGenerationController {

    public static final int CODE_SUCCESS = 0;
    public static final int CODE_PROCESSING = 1001;
    public static final int CODE_VALIDATION_ERROR = 4001;
    public static final int CODE_PROVIDER_ERROR = 5001;

    private final ExternalVideoGenerationService service;

    /**
     * 创建视频生成任务；返回 202 processing 外部信封。
     */
    @PostMapping("/videos")
    @Operation(summary = "创建视频生成任务", description = "校验 + external_request_id 幂等复用；返回 202 processing")
    public ResponseEntity<ExternalVideoGenerationEnvelope> create(
            @RequestBody ExternalVideoGenerationCreateRequest request) {
        CreateVideoJobResult result = service.createJob(CreateVideoGenerationCommand.of(
                request.getExternalRequestId(),
                request.getImages(),
                request.getStoryboardText(),
                request.getDurationSeconds(),
                request.getAspectRatio(),
                request.getProductName(),
                request.getBrief(),
                request.getPrompt(),
                request.getLanguage()));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("job_id", result.jobId());
        data.put("status", "processing");
        data.put("duration_seconds", result.durationSeconds());
        data.put("aspect_ratio", result.aspectRatio());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new ExternalVideoGenerationEnvelope(CODE_PROCESSING, "processing", data));
    }

    /**
     * 查询视频生成任务；找到 → 200，未找到 → 404。GET 只读 DB，不刷新上游。
     */
    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "查询视频生成任务", description = "找到 → 200；未找到 → 404；只读库")
    public ResponseEntity<ExternalVideoGenerationEnvelope> get(@PathVariable String jobId) {
        VideoGenerationJobView view = service.getJob(jobId);
        ExternalVideoGenerationJobVO vo = toJobVo(view);
        int code;
        String message;
        switch (view.status()) {
            case "succeeded" -> {
                code = CODE_SUCCESS;
                message = "success";
            }
            case "failed" -> {
                code = CODE_PROVIDER_ERROR;
                message = "video generation failed";
            }
            default -> {
                code = CODE_PROCESSING;
                message = "processing";
            }
        }
        return ResponseEntity.ok(new ExternalVideoGenerationEnvelope(code, message, vo));
    }

    private static ExternalVideoGenerationJobVO toJobVo(VideoGenerationJobView view) {
        return new ExternalVideoGenerationJobVO(
                view.jobId(), view.status(), view.durationSeconds(), view.aspectRatio(), view.url(), view.error());
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ExternalVideoGenerationEnvelope> handleBiz(BizException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ExternalVideoGenerationEnvelope(
                        CODE_VALIDATION_ERROR, ex.getMessage(), Map.of("errors", List.of(ex.getMessage()))));
    }

    @ExceptionHandler(VideoGenerationTaskNotFoundException.class)
    public ResponseEntity<ExternalVideoGenerationEnvelope> handleNotFound(VideoGenerationTaskNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ExternalVideoGenerationEnvelope(CODE_VALIDATION_ERROR, ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ExternalVideoGenerationEnvelope> handleNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ExternalVideoGenerationEnvelope(CODE_VALIDATION_ERROR, "request body is invalid", Map.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExternalVideoGenerationEnvelope> handleAny(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ExternalVideoGenerationEnvelope(CODE_PROVIDER_ERROR, "video generation failed", Map.of()));
    }
}

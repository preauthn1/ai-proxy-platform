package com.yunpei.proxy.api.controller;

import com.yunpei.proxy.api.dto.ExternalImageGenerationCreateRequest;
import com.yunpei.proxy.api.dto.ExternalImageRevisionCreateRequest;
import com.yunpei.proxy.api.vo.ExternalImageGenerationEnvelope;
import com.yunpei.proxy.api.vo.ExternalImageGenerationJobVO;
import com.yunpei.proxy.api.vo.GeneratedImageVO;
import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.service.externalimage.CreateImageEditCommand;
import com.yunpei.proxy.service.externalimage.CreateImageGenerationCommand;
import com.yunpei.proxy.service.externalimage.CreateImageRevisionCommand;
import com.yunpei.proxy.service.externalimage.CreateJobResult;
import com.yunpei.proxy.service.externalimage.ExternalImageGenerationService;
import com.yunpei.proxy.service.externalimage.GenerationJobView;
import com.yunpei.proxy.service.externalimage.GenerationTaskNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * 图片生成外部集成端点（对齐 Python {@code ExternalImageGenerationRoute}）。
 *
 * <p>返回<strong>外部信封</strong>而非全局 {@code Result}；校验/业务错误 → HTTP 400 code=4001，
 * 未找到 → HTTP 404 code=4001，未知 → HTTP 500 code=5001。鉴权由 {@code AiAdsAccessTokenFilter} 完成。
 *
 * @author yunpei
 */
@Tag(name = "图片生成集成", description = "外部共享密钥鉴权；外部信封")
@RestController
@RequestMapping("/api/v1/integrations/image-generation")
@RequiredArgsConstructor
public class ExternalImageGenerationController {

    /** code：0=success。 */
    public static final int CODE_SUCCESS = 0;
    /** code：1001=processing。 */
    public static final int CODE_PROCESSING = 1001;
    /** code：4001=validation_error。 */
    public static final int CODE_VALIDATION_ERROR = 4001;
    /** code：5001=provider_error。 */
    public static final int CODE_PROVIDER_ERROR = 5001;

    private final ExternalImageGenerationService service;

    /**
     * 创建图片生成任务；返回 202 processing 外部信封。
     *
     * @param request 创建请求（prompt/count/size/model_id/external_request_id）
     * @return 202 信封，data 含 job_id/status/count/size
     */
    @PostMapping("/images")
    @Operation(summary = "创建图片生成任务", description = "校验 + external_request_id 幂等复用；返回 202 processing 信封")
    public ResponseEntity<ExternalImageGenerationEnvelope> create(
            @RequestBody ExternalImageGenerationCreateRequest request) {
        CreateJobResult result = service.createJob(CreateImageGenerationCommand.of(
                request.getExternalRequestId(),
                request.getPrompt(),
                request.getCount(),
                request.getSize(),
                request.getModelId()));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(creationEnvelope(result));
    }

    /**
     * 上传源图编辑；multipart：image + prompt + 可选字段。
     *
     * @param image 源图文件
     * @param prompt 编辑提示
     * @param count 数量
     * @param size 尺寸
     * @param modelId 模型
     * @param externalRequestId 幂等键
     * @return 202 processing 信封，含 mode=from_image
     */
    @PostMapping(value = "/edits", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传图编辑", description = "multipart 上传源图 + prompt；返回 202 processing")
    public ResponseEntity<ExternalImageGenerationEnvelope> createEdit(
            @RequestParam("image") MultipartFile image,
            @RequestParam("prompt") String prompt,
            @RequestParam(value = "count", required = false) Integer count,
            @RequestParam(value = "size", required = false) String size,
            @RequestParam(value = "model_id", required = false) String modelId,
            @RequestParam(value = "external_request_id", required = false) String externalRequestId)
            throws Exception {
        if (image == null || image.isEmpty()) {
            throw BizException.of(com.yunpei.proxy.common.result.ResultCode.PARAM_INVALID, "Uploaded image is empty");
        }
        byte[] bytes = image.getBytes();
        CreateJobResult result = service.createEditJob(new CreateImageEditCommand(
                bytes, image.getContentType(), prompt, count, size, modelId, externalRequestId));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(creationEnvelope(result));
    }

    /**
     * 基于已成功 job 创建修订任务。
     *
     * @param sourceJobId 源 job id
     * @param request 修订请求
     * @return 202 processing 信封，含 source_job_id / mode
     */
    @PostMapping("/jobs/{sourceJobId}/revisions")
    @Operation(summary = "创建图片修订任务", description = "源 job 须 succeeded；返回 202 processing")
    public ResponseEntity<ExternalImageGenerationEnvelope> createRevision(
            @PathVariable String sourceJobId, @RequestBody ExternalImageRevisionCreateRequest request) {
        CreateJobResult result = service.createRevisionJob(new CreateImageRevisionCommand(
                sourceJobId,
                request.getFeedback(),
                request.getSourceImageIndex(),
                request.getCount(),
                request.getSize(),
                request.getModelId(),
                request.getExternalRequestId()));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(creationEnvelope(result));
    }

    /**
     * 查询图片生成任务；找到 → 200，未找到 → 404。
     *
     * @param jobId 任务 id
     * @return 200 信封（code 按 status：succeeded=0 / failed=5001 / 其余 1001）
     */
    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "查询图片生成任务", description = "找到 → 200；未找到 → 404")
    public ResponseEntity<ExternalImageGenerationEnvelope> get(@PathVariable String jobId) {
        GenerationJobView view = service.getJob(jobId);
        ExternalImageGenerationJobVO vo = toJobVo(view);
        int code;
        String message;
        switch (view.status()) {
            case "succeeded" -> {
                code = CODE_SUCCESS;
                message = "success";
            }
            case "failed" -> {
                code = CODE_PROVIDER_ERROR;
                message = "image generation failed";
            }
            default -> {
                code = CODE_PROCESSING;
                message = "processing";
            }
        }
        return ResponseEntity.ok(new ExternalImageGenerationEnvelope(code, message, vo));
    }

    private static ExternalImageGenerationEnvelope creationEnvelope(CreateJobResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("job_id", result.jobId());
        // 创建信封状态恒为 processing（接受未执行；幂等复用亦然）
        data.put("status", "processing");
        data.put("count", result.count());
        data.put("size", result.size());
        if (result.sourceJobId() != null && !result.sourceJobId().isBlank()) {
            data.put("source_job_id", result.sourceJobId());
        }
        if (result.mode() != null && !result.mode().isBlank()) {
            data.put("mode", result.mode());
        }
        return new ExternalImageGenerationEnvelope(CODE_PROCESSING, "processing", data);
    }

    private static ExternalImageGenerationJobVO toJobVo(GenerationJobView view) {
        List<GeneratedImageVO> images = view.images() == null
                ? List.of()
                : view.images().stream()
                        .map(i -> new GeneratedImageVO(i.index(), i.url(), i.prompt()))
                        .toList();
        return new ExternalImageGenerationJobVO(
                view.jobId(),
                view.status(),
                view.count(),
                view.size(),
                view.modelId(),
                images,
                view.error(),
                view.sourceJobId(),
                view.mode());
    }

    // ---- 局部异常映射：外部信封，不落入全局 Result ----

    /** 业务校验错误 → 400，code=4001。 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ExternalImageGenerationEnvelope> handleBiz(BizException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ExternalImageGenerationEnvelope(
                        CODE_VALIDATION_ERROR, ex.getMessage(), Map.of("errors", List.of(ex.getMessage()))));
    }

    /** 任务未找到 → 404，code=4001。 */
    @ExceptionHandler(GenerationTaskNotFoundException.class)
    public ResponseEntity<ExternalImageGenerationEnvelope> handleNotFound(GenerationTaskNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ExternalImageGenerationEnvelope(CODE_VALIDATION_ERROR, ex.getMessage(), Map.of()));
    }

    /** 请求体不可读（JSON 格式错误）→ 400，code=4001。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ExternalImageGenerationEnvelope> handleNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ExternalImageGenerationEnvelope(CODE_VALIDATION_ERROR, "request body is invalid", Map.of()));
    }

    /** multipart 缺文件 → 400。 */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ExternalImageGenerationEnvelope> handleMissingPart(MissingServletRequestPartException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ExternalImageGenerationEnvelope(
                        CODE_VALIDATION_ERROR, "request validation failed", Map.of()));
    }

    /** 缺少必填参数 → 400。 */
    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<ExternalImageGenerationEnvelope> handleMissingParam(
            org.springframework.web.bind.MissingServletRequestParameterException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ExternalImageGenerationEnvelope(
                        CODE_VALIDATION_ERROR, "request validation failed", Map.of()));
    }

    /** 未知异常 → 500，code=5001，不暴露内部细节。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExternalImageGenerationEnvelope> handleAny(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ExternalImageGenerationEnvelope(CODE_PROVIDER_ERROR, "image generation failed", Map.of()));
    }
}

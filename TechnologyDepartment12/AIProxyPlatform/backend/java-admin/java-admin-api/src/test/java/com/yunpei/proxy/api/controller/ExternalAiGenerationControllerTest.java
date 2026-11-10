package com.yunpei.proxy.api.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.infra.security.AiAdsAccessTokenFilter;
import com.yunpei.proxy.infra.security.AiAdsAccessTokenProperties;
import com.yunpei.proxy.service.externalai.AiGenerationJobView;
import com.yunpei.proxy.service.externalai.AiGenerationTaskNotFoundException;
import com.yunpei.proxy.service.externalai.CreateAiJobCommand;
import com.yunpei.proxy.service.externalai.CreateAiJobResult;
import com.yunpei.proxy.service.externalai.ExternalAiGenerationService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * {@link ExternalAiGenerationController} 契约测试（Seam S1）：鉴权 / 202 / 400 / GET 映射。
 */
class ExternalAiGenerationControllerTest {

    private static final String AUTH = "Authorization";

    private final ExternalAiGenerationService service = mock(ExternalAiGenerationService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void init() {
        AiAdsAccessTokenProperties properties = new AiAdsAccessTokenProperties();
        properties.setAccessToken("secret");
        ExternalAiGenerationController controller = new ExternalAiGenerationController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(new AiAdsAccessTokenFilter(properties))
                .build();
    }

    @Test
    void createWorkOrderAnalysis_returns202WithAsyTaskIdAndRetryAfter() throws Exception {
        when(service.createJob(ArgumentMatchers.any(CreateAiJobCommand.class)))
                .thenReturn(new CreateAiJobResult("job-wo-1", "processing"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/integrations/ai/work-order-analysis")
                        .header(AUTH, "Bearer secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"work_order_type\":\"game\",\"work_order_text\":\"客户通过 WhatsApp 咨询\","
                                + "\"media\":\"fb\",\"external_request_id\":\"req-wo\"}"))
                .andExpect(MockMvcResultMatchers.status().isAccepted())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(1001))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.asy_task_id").value("job-wo-1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.job_id").value("job-wo-1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.status").value("processing"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.retry_after_seconds")
                        .value(2));
    }

    @Test
    void createWorkOrderAnalysis_bizError_returns400ValidationEnvelope() throws Exception {
        when(service.createJob(ArgumentMatchers.any(CreateAiJobCommand.class)))
                .thenThrow(new BizException(ResultCode.PARAM_INVALID, "work_order_text is required"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/integrations/ai/work-order-analysis")
                        .header(AUTH, "Bearer secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"work_order_type\":\"game\",\"work_order_text\":\"\",\"media\":\"fb\"}"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(4001))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("work_order_text is required"));
    }

    @Test
    void get_succeededWorkOrderAnalysis_flattensV3Keys() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("request_id", "job-wo-2");
        result.put("schema_version", "3.0");
        result.put("detected_objective", "OUTCOME_ENGAGEMENT");
        result.put("campaign_payload", Map.of("objective", "OUTCOME_ENGAGEMENT", "status", "PAUSED"));
        result.put("adset_payload", Map.of("optimization_goal", "CONVERSATIONS"));
        result.put("creative_payload", Map.of("btn_type", "MESSAGE_PAGE"));
        result.put("review", Map.of("requires_user_review", false));
        when(service.getJob("job-wo-2")).thenReturn(new AiGenerationJobView("job-wo-2", "succeeded", result, null));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/integrations/ai/jobs/job-wo-2")
                        .header(AUTH, "Bearer secret"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.request_id").value("job-wo-2"))
                .andExpect(
                        MockMvcResultMatchers.jsonPath("$.data.schema_version").value("3.0"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.detected_objective")
                        .value("OUTCOME_ENGAGEMENT"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.campaign_payload.objective")
                        .value("OUTCOME_ENGAGEMENT"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.adset_payload.optimization_goal")
                        .value("CONVERSATIONS"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.creative_payload.btn_type")
                        .value("MESSAGE_PAGE"));
    }

    @Test
    void createTopics_endpointRemoved_returns404() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/integrations/ai/topics")
                        .header(AUTH, "Bearer secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product_name\":\"Demo Product\"}"))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void createStoryboard_endpointRemoved_returns404() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/integrations/ai/storyboard")
                        .header(AUTH, "Bearer secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product_name\":\"Demo Product\"}"))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void createCopy_returns202WithAsyTaskIdAndRetryAfter() throws Exception {
        when(service.createJob(ArgumentMatchers.any(CreateAiJobCommand.class)))
                .thenReturn(new CreateAiJobResult("job-copy-1", "processing"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/integrations/ai/copy")
                        .header(AUTH, "Bearer secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product_name\":\"Demo Product\",\"count\":2,\"customEventType\":\"PURCHASE\"}"))
                .andExpect(MockMvcResultMatchers.status().isAccepted())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(1001))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.asy_task_id").value("job-copy-1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.job_id").value("job-copy-1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.status").value("processing"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.retry_after_seconds")
                        .value(2));
    }

    @Test
    void createCopy_acceptsCustomEventTypeCamelAlias() throws Exception {
        org.mockito.ArgumentCaptor<CreateAiJobCommand> captor =
                org.mockito.ArgumentCaptor.forClass(CreateAiJobCommand.class);
        when(service.createJob(captor.capture())).thenReturn(new CreateAiJobResult("job-copy-alias", "processing"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/integrations/ai/copy")
                        .header(AUTH, "Bearer secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product_name\":\"Demo\",\"customEventType\":\"COMPLETE_REGISTRATION\"}"))
                .andExpect(MockMvcResultMatchers.status().isAccepted());

        CreateAiJobCommand cmd = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(cmd.taskType())
                .isEqualTo(com.yunpei.proxy.service.externalai.ExternalAiTaskTypes.COPY_GENERATION);
        org.assertj.core.api.Assertions.assertThat(cmd.payload().get("customEventType"))
                .isEqualTo("COMPLETE_REGISTRATION");
    }

    @Test
    void createCopy_acceptsCustomEventTypeSnakeAlias() throws Exception {
        org.mockito.ArgumentCaptor<CreateAiJobCommand> captor =
                org.mockito.ArgumentCaptor.forClass(CreateAiJobCommand.class);
        when(service.createJob(captor.capture())).thenReturn(new CreateAiJobResult("job-copy-snake", "processing"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/integrations/ai/copy")
                        .header(AUTH, "Bearer secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product_name\":\"Demo\",\"custom_event_type\":\"PURCHASE\"}"))
                .andExpect(MockMvcResultMatchers.status().isAccepted());

        org.assertj.core.api.Assertions.assertThat(captor.getValue().payload().get("customEventType"))
                .isEqualTo("PURCHASE");
    }

    @Test
    void get_succeededCopy_flattensRequestIdAndCopywritings() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("request_id", "ext-copy-1");
        result.put(
                "copywritings",
                java.util.List.of(Map.of(
                        "primary_text",
                        "Explore practical ways Demo can fit your day.",
                        "headline",
                        "Discover Demo today",
                        "description",
                        "See useful features in one place",
                        "cta",
                        "LEARN_MORE",
                        "call_to_action",
                        "LEARN_MORE",
                        "customEventType",
                        "PURCHASE")));
        when(service.getJob("job-copy")).thenReturn(new AiGenerationJobView("job-copy", "succeeded", result, null));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/integrations/ai/jobs/job-copy")
                        .header(AUTH, "Bearer secret"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.request_id").value("ext-copy-1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.copywritings[0].headline")
                        .value("Discover Demo today"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.copywritings[0].customEventType")
                        .value("PURCHASE"));
    }

    @Test
    void create_withoutToken_returns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/integrations/ai/copy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product_name\":\"Demo Product\"}"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(4001))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("unauthorized"));
    }

    @Test
    void create_bizError_returns400ValidationEnvelope() throws Exception {
        when(service.createJob(ArgumentMatchers.any(CreateAiJobCommand.class)))
                .thenThrow(new BizException(ResultCode.PARAM_INVALID, "product_name is required"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/integrations/ai/copy")
                        .header(AUTH, "Bearer secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product_name\":\"\"}"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(4001))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("product_name is required"));
    }

    @Test
    void createCopy_countOutOfRange_returns4001() throws Exception {
        when(service.createJob(ArgumentMatchers.any(CreateAiJobCommand.class)))
                .thenThrow(new BizException(ResultCode.PARAM_INVALID, "count must be between 1 and 5"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/integrations/ai/copy")
                        .header(AUTH, "Bearer secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product_name\":\"Demo\",\"count\":9}"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(4001))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("count must be between 1 and 5"));
    }

    @Test
    void get_succeededTopics_flattensRequestIdAndTopics() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("request_id", "ext-t-1");
        result.put(
                "topics",
                java.util.List.of(Map.of(
                        "title",
                        "Demo: Discover a clearer benefit",
                        "angle",
                        "聚焦一个清晰的核心价值，降低理解门槛。",
                        "audience",
                        "面向目标市场的潜在兴趣用户，结合现有投放设置持续测试与优化。",
                        "selling_points",
                        java.util.List.of("围绕产品核心价值建立清晰、可测试的广告沟通。"),
                        "rationale",
                        "将产品价值与当前投放目标结合，便于在 Facebook 广告中测试用户反馈。")));
        when(service.getJob("job-topics")).thenReturn(new AiGenerationJobView("job-topics", "succeeded", result, null));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/integrations/ai/jobs/job-topics")
                        .header(AUTH, "Bearer secret"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.request_id").value("ext-t-1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.topics[0].title")
                        .value("Demo: Discover a clearer benefit"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.topics[0].selling_points[0]")
                        .value("围绕产品核心价值建立清晰、可测试的广告沟通。"));
    }

    @Test
    void get_processing_returns1001WithRetryAfter() throws Exception {
        when(service.getJob("job-1")).thenReturn(new AiGenerationJobView("job-1", "processing", Map.of(), null));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/integrations/ai/jobs/job-1")
                        .header(AUTH, "Bearer secret"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(1001))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.asy_task_id").value("job-1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.job_id").value("job-1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.status").value("processing"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.retry_after_seconds")
                        .value(2));
    }

    @Test
    void get_succeeded_flattensResultIntoData() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("request_id", "job-2");
        result.put("topics", java.util.List.of(Map.of("title", "T1")));
        when(service.getJob("job-2")).thenReturn(new AiGenerationJobView("job-2", "succeeded", result, null));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/integrations/ai/jobs/job-2")
                        .header(AUTH, "Bearer secret"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.asy_task_id").value("job-2"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.job_id").value("job-2"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.status").value("succeeded"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.request_id").value("job-2"))
                .andExpect(
                        MockMvcResultMatchers.jsonPath("$.data.topics[0].title").value("T1"))
                // result 展平进 data，不出现嵌套 result 包
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.result").doesNotExist());
    }

    @Test
    void get_succeeded_resultKeysDoNotOverrideIdentityFields() throws Exception {
        // 对齐 Controller：putAll(result) 后再写回 asy_task_id/job_id/status
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("asy_task_id", "from-result");
        result.put("job_id", "from-result");
        result.put("status", "from-result");
        result.put("request_id", "ext-1");
        when(service.getJob("job-id-keep"))
                .thenReturn(new AiGenerationJobView("job-id-keep", "succeeded", result, null));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/integrations/ai/jobs/job-id-keep")
                        .header(AUTH, "Bearer secret"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.asy_task_id").value("job-id-keep"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.job_id").value("job-id-keep"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.status").value("succeeded"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.request_id").value("ext-1"));
    }

    @Test
    void get_failed_returns5001WithError() throws Exception {
        when(service.getJob("job-3")).thenReturn(new AiGenerationJobView("job-3", "failed", Map.of(), "boom"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/integrations/ai/jobs/job-3")
                        .header(AUTH, "Bearer secret"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(5001))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.asy_task_id").value("job-3"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.job_id").value("job-3"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.status").value("failed"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.error").value("boom"));
    }

    @Test
    void get_notFound_returns404() throws Exception {
        when(service.getJob("nope")).thenThrow(new AiGenerationTaskNotFoundException("ai generation job not found"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/integrations/ai/jobs/nope")
                        .header(AUTH, "Bearer secret"))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(4001));
    }

    @Test
    void get_wrongTaskTypeJob_returns404() throws Exception {
        // 图片/视频等非文本类 job 经 AI GET 查询 → 与未知 job 相同 404 信封
        when(service.getJob("img-job")).thenThrow(new AiGenerationTaskNotFoundException("ai generation job not found"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/integrations/ai/jobs/img-job")
                        .header(AUTH, "Bearer secret"))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(4001))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("ai generation job not found"));
    }

    @Test
    void create_idempotentReuse_returnsSameAsyTaskId() throws Exception {
        when(service.createJob(ArgumentMatchers.any(CreateAiJobCommand.class)))
                .thenReturn(new CreateAiJobResult("job-reuse-1", "processing"));

        String body = "{\"product_name\":\"Demo\",\"external_request_id\":\"req-hard-1\"}";
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/integrations/ai/copy")
                        .header(AUTH, "Bearer secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isAccepted())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.asy_task_id").value("job-reuse-1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.retry_after_seconds")
                        .value(2));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/integrations/ai/copy")
                        .header(AUTH, "Bearer secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isAccepted())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(1001))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.asy_task_id").value("job-reuse-1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.job_id").value("job-reuse-1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.status").value("processing"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.retry_after_seconds")
                        .value(2));
    }

    @Test
    void get_succeededStoryboard_flattensResultKeys() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("request_id", "ext-sb-1");
        result.put("storyboard_text", "Format: 12 seconds, 9:16, one continuous clip.");
        result.put("duration_seconds", 12);
        result.put("aspect_ratio", "9:16");
        when(service.getJob("job-sb")).thenReturn(new AiGenerationJobView("job-sb", "succeeded", result, null));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/integrations/ai/jobs/job-sb")
                        .header(AUTH, "Bearer secret"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.request_id").value("ext-sb-1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.storyboard_text")
                        .value("Format: 12 seconds, 9:16, one continuous clip."))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.duration_seconds")
                        .value(12))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.aspect_ratio").value("9:16"));
    }
}

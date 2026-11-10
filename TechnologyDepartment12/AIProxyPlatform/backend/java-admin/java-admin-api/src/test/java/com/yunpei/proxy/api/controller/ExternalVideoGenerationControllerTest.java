package com.yunpei.proxy.api.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.infra.security.AiAdsAccessTokenFilter;
import com.yunpei.proxy.infra.security.AiAdsAccessTokenProperties;
import com.yunpei.proxy.service.externalvideo.CreateVideoGenerationCommand;
import com.yunpei.proxy.service.externalvideo.CreateVideoJobResult;
import com.yunpei.proxy.service.externalvideo.ExternalVideoGenerationService;
import com.yunpei.proxy.service.externalvideo.VideoGenerationJobView;
import com.yunpei.proxy.service.externalvideo.VideoGenerationTaskNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * {@link ExternalVideoGenerationController} 契约测试。
 */
class ExternalVideoGenerationControllerTest {

    private static final String AUTH = "Authorization";

    private final ExternalVideoGenerationService service = mock(ExternalVideoGenerationService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void init() {
        AiAdsAccessTokenProperties properties = new AiAdsAccessTokenProperties();
        properties.setAccessToken("secret");
        ExternalVideoGenerationController controller = new ExternalVideoGenerationController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(new AiAdsAccessTokenFilter(properties))
                .build();
    }

    @Test
    void create_returns202ProcessingEnvelope() throws Exception {
        when(service.createJob(ArgumentMatchers.any(CreateVideoGenerationCommand.class)))
                .thenReturn(new CreateVideoJobResult("job-1", "processing", 12, "9:16"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/integrations/video-generation/videos")
                        .header(AUTH, "Bearer secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storyboard_text\":\"scene one\"}"))
                .andExpect(MockMvcResultMatchers.status().isAccepted())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(1001))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("processing"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.job_id").value("job-1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.status").value("processing"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.duration_seconds")
                        .value(12))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.aspect_ratio").value("9:16"));
    }

    @Test
    void create_withoutToken_returns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/integrations/video-generation/videos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storyboard_text\":\"scene one\"}"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(4001));
    }

    @Test
    void create_bizError_returns400ValidationEnvelope() throws Exception {
        when(service.createJob(ArgumentMatchers.any(CreateVideoGenerationCommand.class)))
                .thenThrow(new BizException(ResultCode.PARAM_INVALID, "storyboard_text or product_name is required"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/integrations/video-generation/videos")
                        .header(AUTH, "Bearer secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storyboard_text\":\"\"}"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(4001))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value("storyboard_text or product_name is required"));
    }

    @Test
    void create_withProductNameOnly_returns202() throws Exception {
        when(service.createJob(ArgumentMatchers.any(CreateVideoGenerationCommand.class)))
                .thenReturn(new CreateVideoJobResult("job-2", "processing", 12, "9:16"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/integrations/video-generation/videos")
                        .header(AUTH, "Bearer secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product_name\":\"Demo\",\"brief\":\"opening energy\"}"))
                .andExpect(MockMvcResultMatchers.status().isAccepted())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(1001))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.job_id").value("job-2"));
    }

    @Test
    void get_returnsProcessingEnvelope() throws Exception {
        when(service.getJob("job-1"))
                .thenReturn(new VideoGenerationJobView("job-1", "processing", 12, "9:16", null, null));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/integrations/video-generation/jobs/job-1")
                        .header(AUTH, "Bearer secret"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(1001))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.job_id").value("job-1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.status").value("processing"));
    }

    @Test
    void get_succeeded_returnsUrl() throws Exception {
        when(service.getJob("job-1"))
                .thenReturn(new VideoGenerationJobView(
                        "job-1", "succeeded", 12, "9:16", "https://video.example/a.mp4", null));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/integrations/video-generation/jobs/job-1")
                        .header(AUTH, "Bearer secret"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.url").value("https://video.example/a.mp4"));
    }

    @Test
    void get_notFound_returns404() throws Exception {
        when(service.getJob("nope")).thenThrow(new VideoGenerationTaskNotFoundException("video job not found"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/integrations/video-generation/jobs/nope")
                        .header(AUTH, "Bearer secret"))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(4001));
    }
}

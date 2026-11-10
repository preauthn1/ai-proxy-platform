package com.yunpei.proxy.api.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.infra.security.AiAdsAccessTokenFilter;
import com.yunpei.proxy.infra.security.AiAdsAccessTokenProperties;
import com.yunpei.proxy.service.externalimage.CreateImageEditCommand;
import com.yunpei.proxy.service.externalimage.CreateImageGenerationCommand;
import com.yunpei.proxy.service.externalimage.CreateImageRevisionCommand;
import com.yunpei.proxy.service.externalimage.CreateJobResult;
import com.yunpei.proxy.service.externalimage.ExternalImageGenerationService;
import com.yunpei.proxy.service.externalimage.GenerationJobView;
import com.yunpei.proxy.service.externalimage.GenerationTaskNotFoundException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * {@link ExternalImageGenerationController} 契约测试：202 信封 / 401 鉴权 / GET / edits / revisions。
 */
class ExternalImageGenerationControllerTest {

    private static final String AUTH = "Authorization";

    private final ExternalImageGenerationService service = mock(ExternalImageGenerationService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void init() {
        AiAdsAccessTokenProperties properties = new AiAdsAccessTokenProperties();
        properties.setAccessToken("secret");
        ExternalImageGenerationController controller = new ExternalImageGenerationController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(new AiAdsAccessTokenFilter(properties))
                .build();
    }

    @Test
    void create_returns202ProcessingEnvelope() throws Exception {
        when(service.createJob(ArgumentMatchers.any(CreateImageGenerationCommand.class)))
                .thenReturn(CreateJobResult.of("job-1", "processing", 1, "9:16"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/integrations/image-generation/images")
                        .header(AUTH, "Bearer secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"a cat\"}"))
                .andExpect(MockMvcResultMatchers.status().isAccepted())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(1001))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("processing"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.job_id").value("job-1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.status").value("processing"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.count").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.size").value("9:16"));
    }

    @Test
    void create_withoutToken_returns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/integrations/image-generation/images")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"a cat\"}"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(4001));
    }

    @Test
    void create_bizError_returns400ValidationEnvelope() throws Exception {
        when(service.createJob(ArgumentMatchers.any(CreateImageGenerationCommand.class)))
                .thenThrow(new BizException(ResultCode.PARAM_INVALID, "prompt is required"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/integrations/image-generation/images")
                        .header(AUTH, "Bearer secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"\"}"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(4001))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("prompt is required"));
    }

    @Test
    void get_returnsProcessingEnvelope() throws Exception {
        when(service.getJob("job-1"))
                .thenReturn(new GenerationJobView("job-1", "processing", 1, "9:16", null, List.of(), null, null, null));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/integrations/image-generation/jobs/job-1")
                        .header(AUTH, "Bearer secret"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(1001))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.job_id").value("job-1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.status").value("processing"));
    }

    @Test
    void get_notFound_returns404() throws Exception {
        when(service.getJob("nope")).thenThrow(new GenerationTaskNotFoundException("image generation job not found"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/integrations/image-generation/jobs/nope")
                        .header(AUTH, "Bearer secret"))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(4001));
    }

    @Test
    void createEdit_returns202WithModeFromImage() throws Exception {
        when(service.createEditJob(ArgumentMatchers.any(CreateImageEditCommand.class)))
                .thenReturn(new CreateJobResult("job-edit", "processing", 1, "1:1", "from_image", null));

        // minimal PNG
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MockMultipartFile image = new MockMultipartFile("image", "source.png", "image/png", png);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/integrations/image-generation/edits")
                        .file(image)
                        .param("prompt", "brighter background")
                        .param("size", "1:1")
                        .header(AUTH, "Bearer secret"))
                .andExpect(MockMvcResultMatchers.status().isAccepted())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(1001))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.job_id").value("job-edit"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.mode").value("from_image"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.size").value("1:1"));
    }

    @Test
    void createEdit_withoutImage_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/integrations/image-generation/edits")
                        .param("prompt", "brighter")
                        .header(AUTH, "Bearer secret"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(4001));
    }

    @Test
    void createRevision_returns202WithSourceJobId() throws Exception {
        when(service.createRevisionJob(ArgumentMatchers.any(CreateImageRevisionCommand.class)))
                .thenReturn(new CreateJobResult("job-rev", "processing", 1, "9:16", "edit", "job-src"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/integrations/image-generation/jobs/job-src/revisions")
                        .header(AUTH, "Bearer secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feedback\":\"把背景改成红色\",\"external_request_id\":\"rev-1\"}"))
                .andExpect(MockMvcResultMatchers.status().isAccepted())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(1001))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.job_id").value("job-rev"))
                .andExpect(
                        MockMvcResultMatchers.jsonPath("$.data.source_job_id").value("job-src"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.mode").value("edit"));
    }

    @Test
    void createRevision_sourceNotFound_returns404() throws Exception {
        when(service.createRevisionJob(ArgumentMatchers.any(CreateImageRevisionCommand.class)))
                .thenThrow(new GenerationTaskNotFoundException("source image generation job not found"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/integrations/image-generation/jobs/missing/revisions")
                        .header(AUTH, "Bearer secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feedback\":\"改一下\"}"))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(4001));
    }
}

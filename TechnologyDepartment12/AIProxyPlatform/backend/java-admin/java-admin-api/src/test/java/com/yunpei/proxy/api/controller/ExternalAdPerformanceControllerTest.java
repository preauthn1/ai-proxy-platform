package com.yunpei.proxy.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.infra.security.AiAdsAccessTokenFilter;
import com.yunpei.proxy.infra.security.AiAdsAccessTokenProperties;
import com.yunpei.proxy.service.externalad.AdAnalysisDispatchException;
import com.yunpei.proxy.service.externalad.AdAnalysisIdempotencyConflict;
import com.yunpei.proxy.service.externalad.AdAnalysisJobView;
import com.yunpei.proxy.service.externalad.AdAnalysisNotFoundException;
import com.yunpei.proxy.service.externalad.CreateAdAnalysisResult;
import com.yunpei.proxy.service.externalad.ExternalAdPerformanceAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * {@link ExternalAdPerformanceController} 契约：202 / 200 回放 / 409 / 404 / 400 / 503 / 401。
 */
class ExternalAdPerformanceControllerTest {

    private static final String AUTH = "Authorization";
    private static final String PATH = "/api/v1/integrations/ad-performance/analysis-jobs";
    private static final String BODY = """
            {"external_request_id":"job-1","source_type":"external",\
            "campaign":{"name":"new1"},"adset":{"optimization_goal":"LINK_CLICKS"},\
            "creative":{"creative_type":"image","image_url":"https://cdn.example.com/a.jpg"},\
            "insight":{"status":"ACTIVE","spend":"0.24"},"siblings":[]}
            """;

    private final ExternalAdPerformanceAnalysisService service = mock(ExternalAdPerformanceAnalysisService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void init() {
        AiAdsAccessTokenProperties properties = new AiAdsAccessTokenProperties();
        properties.setAccessToken("secret");
        mockMvc = MockMvcBuilders.standaloneSetup(new ExternalAdPerformanceController(service))
                .addFilters(new AiAdsAccessTokenFilter(properties))
                .build();
    }

    @Test
    void create_returns202AcceptedEnvelope() throws Exception {
        when(service.createJob(any()))
                .thenReturn(new CreateAdAnalysisResult(
                        "ana_abc",
                        "job-1",
                        "queued",
                        "queued",
                        "2026-08-14T00:00:00Z",
                        "https://ai.ggcss.xyz/api/v1/integrations/ad-performance/analysis-jobs/ana_abc",
                        false));

        mockMvc.perform(MockMvcRequestBuilders.post(PATH)
                        .header(AUTH, "Bearer secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(MockMvcResultMatchers.status().isAccepted())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(1001))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("accepted"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.analysis_id").value("ana_abc"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.status").value("queued"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.idempotent_replay")
                        .value(false))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.poll_url")
                        .value("https://ai.ggcss.xyz/api/v1/integrations/ad-performance/analysis-jobs/ana_abc"));
    }

    @Test
    void create_replay_returns200() throws Exception {
        when(service.createJob(any()))
                .thenReturn(new CreateAdAnalysisResult(
                        "ana_abc", "job-1", "queued", "queued", "2026-08-14T00:00:00Z", "/p", true));

        mockMvc.perform(MockMvcRequestBuilders.post(PATH)
                        .header(AUTH, "Bearer secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.idempotent_replay")
                        .value(true));
    }

    @Test
    void create_conflict_returns409() throws Exception {
        when(service.createJob(any())).thenThrow(new AdAnalysisIdempotencyConflict());

        mockMvc.perform(MockMvcRequestBuilders.post(PATH)
                        .header(AUTH, "Bearer secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(4001));
    }

    @Test
    void create_validation_returns400() throws Exception {
        when(service.createJob(any()))
                .thenThrow(BizException.of(ResultCode.PARAM_INVALID, "creative.image_url is required"));

        mockMvc.perform(MockMvcRequestBuilders.post(PATH)
                        .header(AUTH, "Bearer secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(4001))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("creative.image_url is required"));
    }

    @Test
    void create_dispatchFailure_returns503() throws Exception {
        when(service.createJob(any()))
                .thenThrow(new AdAnalysisDispatchException("ana_x", "job-1", new RuntimeException()));

        mockMvc.perform(MockMvcRequestBuilders.post(PATH)
                        .header(AUTH, "Bearer secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(MockMvcResultMatchers.status().isServiceUnavailable())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(5001))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.analysis_id").value("ana_x"));
    }

    @Test
    void get_ok() throws Exception {
        when(service.getJob("ana_abc"))
                .thenReturn(new AdAnalysisJobView(
                        "ana_abc", "job-1", "queued", "queued", 0, "2026-08-14T00:00:00Z", null, null, null, null));

        mockMvc.perform(MockMvcRequestBuilders.get(PATH + "/ana_abc").header(AUTH, "Bearer secret"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.analysis_id").value("ana_abc"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.progress").value(0));
    }

    @Test
    void get_missing_returns404() throws Exception {
        when(service.getJob("missing")).thenThrow(new AdAnalysisNotFoundException());

        mockMvc.perform(MockMvcRequestBuilders.get(PATH + "/missing").header(AUTH, "Bearer secret"))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(4001));
    }

    @Test
    void create_withoutToken_returns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(4001));
    }
}

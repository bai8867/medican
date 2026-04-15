package com.campus.diet.controller.admin;

import com.campus.diet.common.GlobalExceptionHandler;
import com.campus.diet.entity.AiIssueSample;
import com.campus.diet.security.LoginUser;
import com.campus.diet.security.LoginUserHolder;
import com.campus.diet.security.Roles;
import com.campus.diet.service.AiIssueSampleService;
import com.campus.diet.service.RuntimeMetricService;
import com.campus.diet.service.SystemKvService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端 AI 质量治理 API 契约（对齐 docs/api-contract.md；需 {@link Roles#ADMIN}）。
 */
@ExtendWith(MockitoExtension.class)
class AdminAiQualityControllerApiContractTest {

    @Mock
    private SystemKvService systemKvService;

    @Mock
    private AiIssueSampleService aiIssueSampleService;

    @Mock
    private RuntimeMetricService runtimeMetricService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new AdminAiQualityController(systemKvService, aiIssueSampleService))
                        .setControllerAdvice(new GlobalExceptionHandler(runtimeMetricService))
                        .build();
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    @Test
    void rules_whenNotLoggedIn_shouldReturn401ApiResponse() throws Exception {
        mockMvc.perform(get("/api/admin/ai-quality/rules"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void rules_whenNotAdmin_shouldReturn403ApiResponse() throws Exception {
        LoginUserHolder.set(new LoginUser(2L, "u", Roles.USER));
        mockMvc.perform(get("/api/admin/ai-quality/rules"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void rules_whenAdmin_shouldExposeQualityFlagsOnData() throws Exception {
        LoginUserHolder.set(new LoginUser(1L, "admin", Roles.ADMIN));
        when(systemKvService.flagOn("ai.quality.guard.enabled", true)).thenReturn(true);
        when(systemKvService.flagOn("ai.quality.safety.strict", true)).thenReturn(false);
        when(systemKvService.get("ai.quality.score.threshold", "75")).thenReturn("82");

        mockMvc.perform(get("/api/admin/ai-quality/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.guardEnabled").value(true))
                .andExpect(jsonPath("$.data.strictSafety").value(false))
                .andExpect(jsonPath("$.data.scoreThreshold").value(82));
    }

    @Test
    void updateRules_whenAdmin_shouldUpsertAndReturnLatestShape() throws Exception {
        LoginUserHolder.set(new LoginUser(1L, "admin", Roles.ADMIN));
        when(systemKvService.flagOn("ai.quality.guard.enabled", true)).thenReturn(false);
        when(systemKvService.flagOn("ai.quality.safety.strict", true)).thenReturn(true);
        when(systemKvService.get("ai.quality.score.threshold", "75")).thenReturn("65");

        String body = "{\"guardEnabled\":false,\"strictSafety\":true,\"scoreThreshold\":65}";

        mockMvc.perform(put("/api/admin/ai-quality/rules").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.guardEnabled").value(false))
                .andExpect(jsonPath("$.data.strictSafety").value(true))
                .andExpect(jsonPath("$.data.scoreThreshold").value(65));

        verify(systemKvService).upsert("ai.quality.guard.enabled", "0");
        verify(systemKvService).upsert("ai.quality.safety.strict", "1");
        verify(systemKvService).upsert("ai.quality.score.threshold", "65");
    }

    @Test
    void samples_whenAdmin_shouldReturnPagedEnvelope() throws Exception {
        LoginUserHolder.set(new LoginUser(1L, "admin", Roles.ADMIN));
        AiIssueSample row = new AiIssueSample();
        row.setId(5L);
        row.setSymptom("x");
        when(aiIssueSampleService.listLatest(1, 20, false)).thenReturn(List.of(row));
        when(aiIssueSampleService.count(false)).thenReturn(1L);

        mockMvc.perform(get("/api/admin/ai-quality/samples").param("page", "1").param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(5));
    }

    @Test
    void sampleDetail_whenAdmin_shouldReturnDetailMap() throws Exception {
        LoginUserHolder.set(new LoginUser(1L, "admin", Roles.ADMIN));
        when(aiIssueSampleService.detail(7L)).thenReturn(Map.of("id", 7L, "symptom", "咳"));

        mockMvc.perform(get("/api/admin/ai-quality/samples/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.symptom").value("咳"));
    }

    @Test
    void replay_whenAdmin_shouldReturnReplayPayload() throws Exception {
        LoginUserHolder.set(new LoginUser(1L, "admin", Roles.ADMIN));
        when(aiIssueSampleService.replay(9L)).thenReturn(Map.of("ok", true, "sampleId", 9L));

        mockMvc.perform(post("/api/admin/ai-quality/samples/9/replay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.ok").value(true))
                .andExpect(jsonPath("$.data.sampleId").value(9));
    }
}

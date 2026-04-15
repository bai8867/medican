package com.campus.diet.controller;

import com.campus.diet.common.GlobalExceptionHandler;
import com.campus.diet.service.AiDietService;
import com.campus.diet.service.AiTherapyPlanService;
import com.campus.diet.service.RuntimeMetricService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 域 API 契约样例（对齐 docs/api-contract.md 与前端 /ai/generate、药膳 AI 流）。
 */
@ExtendWith(MockitoExtension.class)
class AiDietControllerApiContractTest {

    @Mock
    private AiDietService aiDietService;

    @Mock
    private AiTherapyPlanService aiTherapyPlanService;

    @Mock
    private RuntimeMetricService runtimeMetricService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new AiDietController(aiDietService, aiTherapyPlanService))
                        .setControllerAdvice(new GlobalExceptionHandler(runtimeMetricService))
                        .build();
    }

    @Test
    void dietPlan_shouldReturnApiResponseEnvelope() throws Exception {
        when(aiDietService.generate("头痛乏力")).thenReturn(Map.of("enabled", true, "message", "ok"));

        mockMvc.perform(
                        post("/api/ai/diet-plan")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"symptoms\":\"头痛乏力\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.message").value("ok"));
    }

    @Test
    void generateTherapy_shouldReturnApiResponseWithPlanFields() throws Exception {
        when(aiTherapyPlanService.generate(eq("口干"), eq("yinxu")))
                .thenReturn(
                        Map.of(
                                "planId", "contract-plan-1",
                                "symptomSummary", "口干",
                                "recipes", List.of()));

        mockMvc.perform(
                        post("/api/ai/generate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"symptom\":\"口干\",\"constitution\":\"yinxu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.planId").value("contract-plan-1"))
                .andExpect(jsonPath("$.data.symptomSummary").value("口干"));
    }

    @Test
    void feedback_shouldReturnReceivedFlag() throws Exception {
        mockMvc.perform(post("/api/ai/feedback").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.received").value(true));
    }
}

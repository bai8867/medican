package com.campus.diet.controller.admin;

import com.campus.diet.common.GlobalExceptionHandler;
import com.campus.diet.security.LoginUser;
import com.campus.diet.security.LoginUserHolder;
import com.campus.diet.security.Roles;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端系统开关 API 契约（对齐 docs/api-contract.md；需 {@link Roles#ADMIN}）。
 */
@ExtendWith(MockitoExtension.class)
class AdminSystemControllerApiContractTest {

    @Mock
    private SystemKvService systemKvService;

    @Mock
    private RuntimeMetricService runtimeMetricService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new AdminSystemController(systemKvService))
                        .setControllerAdvice(new GlobalExceptionHandler(runtimeMetricService))
                        .build();
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    @Test
    void flags_whenNotLoggedIn_shouldReturn401ApiResponse() throws Exception {
        mockMvc.perform(get("/api/admin/system/flags"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void flags_whenNotAdmin_shouldReturn403ApiResponse() throws Exception {
        LoginUserHolder.set(new LoginUser(2L, "u", Roles.USER));
        mockMvc.perform(get("/api/admin/system/flags"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void flags_whenAdmin_shouldExposeBooleanFlagsOnData() throws Exception {
        LoginUserHolder.set(new LoginUser(1L, "admin", Roles.ADMIN));
        when(systemKvService.flagOn(eq("recommend.global.enabled"), eq(true))).thenReturn(true);
        when(systemKvService.flagOn(eq("ai.generation.enabled"), eq(true))).thenReturn(false);

        mockMvc.perform(get("/api/admin/system/flags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.recommendGlobalEnabled").value(true))
                .andExpect(jsonPath("$.data.aiGenerationEnabled").value(false));
    }

    @Test
    void setRecommend_whenAdmin_enabledTrue_shouldUpsertAndReturnOk() throws Exception {
        LoginUserHolder.set(new LoginUser(1L, "admin", Roles.ADMIN));

        mockMvc.perform(put("/api/admin/system/recommend").param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.ok").value(true));

        verify(systemKvService).upsert(eq("recommend.global.enabled"), eq("1"));
    }

    @Test
    void setAi_whenAdmin_enabledFalse_shouldUpsertZeroAndReturnOk() throws Exception {
        LoginUserHolder.set(new LoginUser(1L, "admin", Roles.ADMIN));

        mockMvc.perform(put("/api/admin/system/ai").param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.ok").value(true));

        verify(systemKvService).upsert(eq("ai.generation.enabled"), eq("0"));
    }

    @Test
    void kv_whenAdmin_shouldUpsertArbitraryKeyAndReturnOk() throws Exception {
        LoginUserHolder.set(new LoginUser(1L, "admin", Roles.ADMIN));

        mockMvc.perform(
                        put("/api/admin/system/kv")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"k\":\"feature.x\",\"v\":\"beta\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.ok").value(true));

        verify(systemKvService).upsert(eq("feature.x"), eq("beta"));
    }
}

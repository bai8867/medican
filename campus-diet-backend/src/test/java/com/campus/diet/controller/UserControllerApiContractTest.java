package com.campus.diet.controller;

import com.campus.diet.common.GlobalExceptionHandler;
import com.campus.diet.entity.SysUser;
import com.campus.diet.entity.UserProfile;
import com.campus.diet.mapper.SysUserMapper;
import com.campus.diet.security.LoginUser;
import com.campus.diet.security.LoginUserHolder;
import com.campus.diet.security.Roles;
import com.campus.diet.service.ConstitutionSurveyService;
import com.campus.diet.service.ConstitutionSurveyService.SurveyResult;
import com.campus.diet.service.RuntimeMetricService;
import com.campus.diet.service.UserProfileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户域 API 契约样例（对齐 docs/api-contract.md 与前端 profile / preferences）。
 */
@ExtendWith(MockitoExtension.class)
class UserControllerApiContractTest {

    @Mock
    private UserProfileService userProfileService;

    @Mock
    private ConstitutionSurveyService constitutionSurveyService;

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private RuntimeMetricService runtimeMetricService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LoginUserHolder.set(new LoginUser(99L, "demo", Roles.USER));
        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new UserController(userProfileService, constitutionSurveyService, sysUserMapper))
                        .setControllerAdvice(new GlobalExceptionHandler(runtimeMetricService))
                        .build();
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    @Test
    void profile_shouldReturnApiResponseShape() throws Exception {
        SysUser su = new SysUser();
        su.setId(99L);
        su.setUsername("demo");
        su.setRole(Roles.USER);
        when(sysUserMapper.selectById(99L)).thenReturn(su);

        UserProfile p = new UserProfile();
        p.setUserId(99L);
        p.setConstitutionCode("qixu");
        p.setConstitutionSource("survey");
        p.setSeasonCode("spring");
        p.setRecommendEnabled(1);
        p.setDataCollectionEnabled(1);
        p.setSurveyScoresJson("{}");
        when(userProfileService.require(99L)).thenReturn(p);

        mockMvc.perform(get("/api/user/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(99))
                .andExpect(jsonPath("$.data.username").value("demo"))
                .andExpect(jsonPath("$.data.role").value(Roles.USER))
                .andExpect(jsonPath("$.data.constitutionCode").value("qixu"))
                .andExpect(jsonPath("$.data.recommendEnabled").value(true))
                .andExpect(jsonPath("$.data.dataCollectionEnabled").value(true))
                .andExpect(jsonPath("$.data.surveyScoresJson").value("{}"));
    }

    @Test
    void preferences_shouldAcceptBooleanFlags() throws Exception {
        mockMvc.perform(
                        put("/api/user/preferences")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"dataCollectionEnabled\":false,\"recommendEnabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.ok").value(true));

        verify(userProfileService).updatePrivacy(eq(99L), eq(false));
        verify(userProfileService).updateRecommendSwitch(eq(99L), eq(true));
    }

    @Test
    void updateProfile_whenConstitutionCodeProvided_shouldReturnOkAndPersist() throws Exception {
        mockMvc.perform(
                        put("/api/user/profile")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"constitutionCode\":\"yinxu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.ok").value(true));

        verify(userProfileService).updateConstitutionManual(eq(99L), eq("yinxu"));
    }

    @Test
    void submitSurvey_whenLegacyNine_shouldReturnSurveyEnvelope() throws Exception {
        SurveyResult r =
                new SurveyResult(
                        "qixu",
                        "气虚质",
                        Collections.emptyList(),
                        Map.of("qixu", 0.6),
                        Map.of("qixu", 18.0),
                        0.55,
                        List.of("legacy-nine"),
                        ConstitutionSurveyService.LEGACY_VERSION,
                        "{}");

        when(constitutionSurveyService.evaluate(any(), eq(ConstitutionSurveyService.LEGACY_VERSION)))
                .thenReturn(r);

        String body =
                "{\"answers\":[3,3,3,3,3,3,3,3,3],\"seasonCode\":\"summer\",\"questionVersion\":\"legacy-v1\"}";

        mockMvc.perform(
                        post("/api/user/constitution/survey")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.primaryCode").value("qixu"))
                .andExpect(jsonPath("$.data.primaryLabel").value("气虚质"))
                .andExpect(jsonPath("$.data.questionVersion").value(ConstitutionSurveyService.LEGACY_VERSION));

        verify(userProfileService).saveConstitution(eq(99L), eq("qixu"), eq("summer"), eq("{}"));
    }
}

package com.campus.diet.controller;

import com.campus.diet.common.GlobalExceptionHandler;
import com.campus.diet.security.LoginUser;
import com.campus.diet.security.LoginUserHolder;
import com.campus.diet.security.Roles;
import com.campus.diet.service.FeedbackService;
import com.campus.diet.service.RuntimeMetricService;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户反馈 POST 契约样例（匿名可提交；登录则写入 userId）。
 */
@ExtendWith(MockitoExtension.class)
class FeedbackApiControllerApiContractTest {

    @Mock
    private FeedbackService feedbackService;

    @Mock
    private RuntimeMetricService runtimeMetricService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new FeedbackApiController(feedbackService))
                        .setControllerAdvice(new GlobalExceptionHandler(runtimeMetricService))
                        .build();
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    @Test
    void feedback_whenContentMissing_shouldReturn400ApiResponse() throws Exception {
        mockMvc.perform(
                        post("/api/feedback")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"content\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void feedback_whenAnonymous_shouldReturnOkAndSubmitWithNullUser() throws Exception {
        doNothing().when(feedbackService).submit(isNull(), eq("正文"), eq("campus_web"));

        mockMvc.perform(
                        post("/api/feedback")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"content\":\"正文\",\"source\":\"campus_web\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.ok").value(true));

        verify(feedbackService).submit(isNull(), eq("正文"), eq("campus_web"));
    }

    @Test
    void feedback_whenLoggedIn_shouldPassUserIdToService() throws Exception {
        LoginUserHolder.set(new LoginUser(88L, "fb", Roles.USER));
        doNothing().when(feedbackService).submit(eq(88L), eq("登录用户反馈"), isNull());

        mockMvc.perform(
                        post("/api/feedback")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"content\":\"登录用户反馈\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.ok").value(true));

        verify(feedbackService).submit(eq(88L), eq("登录用户反馈"), isNull());
    }
}

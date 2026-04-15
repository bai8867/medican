package com.campus.diet.controller;

import com.campus.diet.common.BizException;
import com.campus.diet.common.GlobalExceptionHandler;
import com.campus.diet.entity.SysUser;
import com.campus.diet.service.AuthService;
import com.campus.diet.service.RuntimeMetricService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private RuntimeMetricService runtimeMetricService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc =
                MockMvcBuilders.standaloneSetup(new AuthController(authService))
                        .setControllerAdvice(new GlobalExceptionHandler(runtimeMetricService))
                        .build();
    }

    @Test
    void login_shouldReturnTokenPayload() throws Exception {
        SysUser user = new SysUser();
        user.setId(10L);
        user.setUsername("demo");
        user.setRole("USER");
        when(authService.login(eq("demo"), eq("secret"))).thenReturn(new AuthService.LoginToken("jwt-here", user));

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"demo\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("jwt-here"))
                .andExpect(jsonPath("$.data.user.id").value(10))
                .andExpect(jsonPath("$.data.user.username").value("demo"))
                .andExpect(jsonPath("$.data.user.role").value("USER"));

        verify(authService).login("demo", "secret");
    }

    @Test
    void login_shouldMapBiz401ToUnauthorized() throws Exception {
        when(authService.login(anyString(), anyString()))
                .thenThrow(new BizException(401, "用户名或密码错误"));

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"x\",\"password\":\"y\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").value("用户名或密码错误"));
    }

    @Test
    void register_shouldReturnTokenPayload() throws Exception {
        SysUser user = new SysUser();
        user.setId(20L);
        user.setUsername("newbie");
        user.setRole("USER");
        when(authService.register(eq("newbie"), eq("pw"), eq("USER")))
                .thenReturn(new AuthService.LoginToken("reg-token", user));

        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                java.util.Map.of(
                                                        "username", "newbie",
                                                        "password", "pw",
                                                        "role", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("reg-token"))
                .andExpect(jsonPath("$.data.user.id").value(20));

        verify(authService).register("newbie", "pw", "USER");
    }

    @Test
    void register_shouldRejectBlankUsernameWith400() throws Exception {
        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"\",\"password\":\"pw\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void logout_shouldReturnOk() throws Exception {
        mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.ok").value(true));
    }
}

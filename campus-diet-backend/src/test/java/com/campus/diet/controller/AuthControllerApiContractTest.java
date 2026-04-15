package com.campus.diet.controller;

import com.campus.diet.common.GlobalExceptionHandler;
import com.campus.diet.entity.SysUser;
import com.campus.diet.security.Roles;
import com.campus.diet.service.AuthService;
import com.campus.diet.service.RuntimeMetricService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证域 API 契约样例（对齐 docs/api-contract.md 与前端 campus/admin 登录流）。
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerApiContractTest {

    @Mock
    private AuthService authService;

    @Mock
    private RuntimeMetricService runtimeMetricService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new AuthController(authService))
                        .setControllerAdvice(new GlobalExceptionHandler(runtimeMetricService))
                        .build();
    }

    @Test
    void login_shouldReturnApiResponseWithTokenAndUser() throws Exception {
        SysUser u = new SysUser();
        u.setId(1L);
        u.setUsername("student");
        u.setRole(Roles.USER);
        when(authService.login("student", "123456")).thenReturn(new AuthService.LoginToken("contract-test-token", u));

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"student\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("contract-test-token"))
                .andExpect(jsonPath("$.data.user.id").value(1))
                .andExpect(jsonPath("$.data.user.username").value("student"))
                .andExpect(jsonPath("$.data.user.role").value(Roles.USER));
    }

    @Test
    void register_shouldReturnSameTokenShape() throws Exception {
        SysUser u = new SysUser();
        u.setId(2L);
        u.setUsername("newuser");
        u.setRole(Roles.USER);
        when(authService.register(eq("newuser"), eq("secret12"), eq(Roles.USER)))
                .thenReturn(new AuthService.LoginToken("reg-contract-token", u));

        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"newuser\",\"password\":\"secret12\",\"role\":\"USER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("reg-contract-token"))
                .andExpect(jsonPath("$.data.user.username").value("newuser"));
    }
}

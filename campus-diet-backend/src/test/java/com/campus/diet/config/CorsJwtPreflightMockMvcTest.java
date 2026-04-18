package com.campus.diet.config;

import com.campus.diet.controller.HealthController;
import com.campus.diet.mapper.SysUserMapper;
import com.campus.diet.security.JwtAuthFilter;
import com.campus.diet.security.JwtService;
import com.campus.diet.security.LoginUserHolder;
import com.campus.diet.service.RuntimeMetricService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 在 {@link MockMvc} 上按 {@link WebConfig} 的<strong>实际顺序</strong>挂载 {@link CorsFilter} → {@link JwtAuthFilter}，
 * 验证浏览器预检（OPTIONS）在<strong>无 Authorization</strong> 时仍能得到 CORS 响应头。补充
 * {@link WebConfigFilterOrderContractTest} 的数值契约，降低「JWT 先于 CORS」回归风险。
 */
@ExtendWith(MockitoExtension.class)
class CorsJwtPreflightMockMvcTest {

    private static final String ALLOWED_ORIGIN_PATTERNS =
            "http://localhost:11999,http://127.0.0.1:11999,http://localhost:5173,http://127.0.0.1:5173";

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private RuntimeMetricService runtimeMetricService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CorsConfiguration config = new CorsConfiguration();
        for (String raw : ALLOWED_ORIGIN_PATTERNS.split(",")) {
            String p = raw == null ? "" : raw.trim();
            if (!p.isEmpty()) {
                config.addAllowedOriginPattern(p);
            }
        }
        config.addAllowedHeader("*");
        config.addAllowedMethod("GET");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("PATCH");
        config.addAllowedMethod("DELETE");
        config.addAllowedMethod("OPTIONS");
        config.setAllowCredentials(true);
        config.addExposedHeader("Authorization");
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        CorsFilter corsFilter = new CorsFilter(source);

        JwtService jwtService = new JwtService("cors-preflight-test-secret-32chars!!", 60);
        ObjectMapper objectMapper = new ObjectMapper();
        JwtAuthFilter jwtAuthFilter = new JwtAuthFilter(jwtService, sysUserMapper, runtimeMetricService, objectMapper);

        mockMvc =
                MockMvcBuilders.standaloneSetup(new HealthController())
                        .addFilters(corsFilter, jwtAuthFilter)
                        .build();
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    @Test
    void optionsApiHealth_withAllowedOrigin_shouldReturnCorsHeaders() throws Exception {
        mockMvc.perform(
                        options("/api/health")
                                .header("Origin", "http://localhost:11999")
                                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:11999"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void getApiHealth_afterPreflightChain_shouldStillReturnJson() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }
}

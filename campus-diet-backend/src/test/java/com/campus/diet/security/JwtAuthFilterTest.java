package com.campus.diet.security;

import com.campus.diet.common.ErrorCodes;
import com.campus.diet.entity.SysUser;
import com.campus.diet.mapper.SysUserMapper;
import com.campus.diet.service.RuntimeMetricService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    private static final String SECRET = "jwt-auth-filter-test-secret-32chars!!";

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private RuntimeMetricService runtimeMetricService;

    private JwtService jwtService;
    private ObjectMapper objectMapper;
    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        LoginUserHolder.clear();
        jwtService = new JwtService(SECRET, 60);
        objectMapper = new ObjectMapper();
        filter = new JwtAuthFilter(jwtService, sysUserMapper, runtimeMetricService, objectMapper);
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    private static FilterChain chainMarking(AtomicBoolean invoked) {
        return (req, res) -> invoked.set(true);
    }

    @Test
    void invalidToken_shouldContinueChainWithoutLoginUser() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/user/profile");
        request.addHeader("Authorization", "Bearer not-a-valid-jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.doFilterInternal(request, response, chainMarking(chainInvoked));

        assertTrue(chainInvoked.get());
        assertEquals(200, response.getStatus());
        assertNull(LoginUserHolder.get());
        verify(runtimeMetricService, never()).increment("error.category.auth.invalid_user");
        verify(runtimeMetricService, never()).increment("error.category.auth.disabled");
    }

    @Test
    void validToken_missingUser_nonWhitelist_shouldReturn401AndNotCallChain() throws Exception {
        String token = jwtService.createToken(999L, "ghost", "USER");
        when(sysUserMapper.selectById(999L)).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/user/profile");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.doFilterInternal(request, response, chainMarking(chainInvoked));

        assertFalse(chainInvoked.get());
        assertEquals(200, response.getStatus());
        verify(runtimeMetricService).increment("error.category.auth.invalid_user");
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertEquals(401, body.get("code").asInt());
    }

    @Test
    void validToken_disabledUser_nonWhitelist_shouldReturn4031AndNotCallChain() throws Exception {
        String token = jwtService.createToken(7L, "banned", "USER");
        SysUser u = new SysUser();
        u.setId(7L);
        u.setStatus(0);
        when(sysUserMapper.selectById(7L)).thenReturn(u);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/user/profile");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.doFilterInternal(request, response, chainMarking(chainInvoked));

        assertFalse(chainInvoked.get());
        assertEquals(200, response.getStatus());
        verify(runtimeMetricService).increment("error.category.auth.disabled");
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertEquals(ErrorCodes.ACCOUNT_DISABLED, body.get("code").asInt());
    }

    @Test
    void validToken_disabledUser_whitelistLogin_shouldCallChain() throws Exception {
        String token = jwtService.createToken(7L, "banned", "USER");
        SysUser u = new SysUser();
        u.setId(7L);
        u.setStatus(0);
        when(sysUserMapper.selectById(7L)).thenReturn(u);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/auth/login");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.doFilterInternal(request, response, chainMarking(chainInvoked));

        assertTrue(chainInvoked.get());
        assertEquals(200, response.getStatus());
        verify(runtimeMetricService, never()).increment("error.category.auth.disabled");
    }

    @Test
    void validToken_activeUser_shouldExposeLoginUserInsideChain() throws Exception {
        String token = jwtService.createToken(3L, "alice", "USER");
        SysUser u = new SysUser();
        u.setId(3L);
        u.setStatus(1);
        when(sysUserMapper.selectById(3L)).thenReturn(u);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/user/profile");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        FilterChain chain =
                (req, res) -> {
                    chainInvoked.set(true);
                    LoginUser lu = LoginUserHolder.get();
                    assertNotNull(lu);
                    assertEquals(3L, lu.getUserId());
                    assertEquals("alice", lu.getUsername());
                    assertEquals("USER", lu.getRole());
                };

        filter.doFilterInternal(request, response, chain);

        assertTrue(chainInvoked.get());
        assertEquals(200, response.getStatus());
    }
}

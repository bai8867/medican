package com.campus.diet.service;

import com.campus.diet.common.BizException;
import com.campus.diet.entity.SysUser;
import com.campus.diet.entity.UserProfile;
import com.campus.diet.mapper.SysUserMapper;
import com.campus.diet.mapper.UserProfileMapper;
import com.campus.diet.security.JwtService;
import com.campus.diet.security.Roles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private UserProfileMapper userProfileMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(sysUserMapper, userProfileMapper, passwordEncoder, jwtService);
    }

    @Test
    void register_shouldRejectAdminSelfRegistration() {
        when(sysUserMapper.selectCount(any())).thenReturn(0L);

        BizException ex =
                assertThrows(BizException.class, () -> authService.register("new_user", "pw123", "ADMIN"));

        assertEquals(400, ex.getCode());
        assertEquals("不允许自助注册管理员", ex.getMessage());
    }

    @Test
    void register_shouldCreateUserProfileWhenRoleIsUser() {
        when(sysUserMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(jwtService.createToken(any(), anyString(), anyString())).thenReturn("token");
        when(sysUserMapper.insert(any(SysUser.class)))
                .thenAnswer(invocation -> {
                    SysUser user = invocation.getArgument(0);
                    user.setId(100L);
                    return 1;
                });

        AuthService.LoginToken token = authService.register("student_a", "pw123", "USER");

        assertNotNull(token);
        assertEquals("token", token.token);
        assertEquals(100L, token.user.getId());
        verify(userProfileMapper).insert(any(UserProfile.class));
    }

    @Test
    void login_shouldRejectDisabledUser() {
        SysUser disabled = new SysUser();
        disabled.setId(2L);
        disabled.setUsername("disabled");
        disabled.setPassword("encoded");
        disabled.setRole(Roles.USER);
        disabled.setStatus(0);

        when(sysUserMapper.selectOne(any())).thenReturn(disabled);

        BizException ex =
                assertThrows(BizException.class, () -> authService.login("disabled", "whatever"));

        assertEquals(4031, ex.getCode());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }
}

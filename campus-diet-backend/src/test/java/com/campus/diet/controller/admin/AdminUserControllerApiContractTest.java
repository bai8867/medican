package com.campus.diet.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.diet.common.GlobalExceptionHandler;
import com.campus.diet.entity.SysUser;
import com.campus.diet.entity.UserProfile;
import com.campus.diet.mapper.RecipeMapper;
import com.campus.diet.mapper.SysUserMapper;
import com.campus.diet.mapper.UserFavoriteMapper;
import com.campus.diet.mapper.UserProfileMapper;
import com.campus.diet.security.LoginUser;
import com.campus.diet.security.LoginUserHolder;
import com.campus.diet.security.Roles;
import com.campus.diet.service.RuntimeMetricService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端学生用户 API 契约（需 {@link Roles#ADMIN}；对齐 docs/api-contract.md 分页与 {@code data} 形状）。
 */
@ExtendWith(MockitoExtension.class)
class AdminUserControllerApiContractTest {

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private UserProfileMapper userProfileMapper;

    @Mock
    private UserFavoriteMapper userFavoriteMapper;

    @Mock
    private RecipeMapper recipeMapper;

    @Mock
    private RuntimeMetricService runtimeMetricService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AdminUserController controller =
                new AdminUserController(
                        sysUserMapper, userProfileMapper, userFavoriteMapper, recipeMapper, objectMapper);
        mockMvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new GlobalExceptionHandler(runtimeMetricService))
                        .build();
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    @Test
    void page_whenNotLoggedIn_shouldReturn401ApiResponse() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void page_whenCanteenManager_shouldReturn403ApiResponse() throws Exception {
        LoginUserHolder.set(new LoginUser(4L, "cm", Roles.CANTEEN_MANAGER));
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void page_whenAdmin_shouldReturnPageResultShape() throws Exception {
        LoginUserHolder.set(new LoginUser(1L, "admin", Roles.ADMIN));

        SysUser u = new SysUser();
        u.setId(501L);
        u.setUsername("stu501");
        u.setRole(Roles.USER);
        u.setStatus(1);
        u.setCreatedAt(LocalDateTime.parse("2026-01-15T10:00:00"));

        Page<SysUser> p = new Page<>(1, 10);
        p.setRecords(List.of(u));
        p.setTotal(1);
        when(sysUserMapper.selectPage(any(Page.class), any())).thenReturn(p);
        when(userProfileMapper.selectList(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/users").param("page", "1").param("page_size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.hasMore").value(false))
                .andExpect(jsonPath("$.data.records[0].id").value("501"))
                .andExpect(jsonPath("$.data.records[0].status").value("active"))
                .andExpect(jsonPath("$.data.records[0].constitutionLabel").exists());
    }

    @Test
    void detail_whenUserMissing_shouldReturn404ApiResponse() throws Exception {
        LoginUserHolder.set(new LoginUser(1L, "admin", Roles.ADMIN));
        when(sysUserMapper.selectById(999L)).thenReturn(null);

        mockMvc.perform(get("/api/admin/users/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void detail_whenStudent_shouldReturnProfileEnvelope() throws Exception {
        LoginUserHolder.set(new LoginUser(1L, "admin", Roles.ADMIN));

        SysUser u = new SysUser();
        u.setId(7L);
        u.setUsername("student7");
        u.setRole(Roles.USER);
        u.setStatus(1);
        u.setCreatedAt(LocalDateTime.parse("2026-02-01T08:30:00"));

        UserProfile prof = new UserProfile();
        prof.setUserId(7L);
        prof.setConstitutionCode("qixu");
        prof.setConstitutionSource("survey");
        prof.setSeasonCode("spring");
        prof.setSurveyScoresJson(null);

        when(sysUserMapper.selectById(7L)).thenReturn(u);
        when(userProfileMapper.selectById(7L)).thenReturn(prof);
        when(userFavoriteMapper.selectList(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/users/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("7"))
                .andExpect(jsonPath("$.data.constitutionCode").value("qixu"))
                .andExpect(jsonPath("$.data.constitutionLabel").value("气虚质"))
                .andExpect(jsonPath("$.data.constitutionSource").value("survey"))
                .andExpect(jsonPath("$.data.seasonCode").value("spring"))
                .andExpect(jsonPath("$.data.seasonLabel").value("春"))
                .andExpect(jsonPath("$.data.status").value("active"))
                .andExpect(jsonPath("$.data.registeredAt", startsWith("2026-02-01T08:30")))
                .andExpect(jsonPath("$.data.surveyScores.constitutionTendency").isMap())
                .andExpect(jsonPath("$.data.favorites.recipes").isArray());
    }

    @Test
    void detail_whenTargetIsNonStudent_shouldReturn400ApiResponse() throws Exception {
        LoginUserHolder.set(new LoginUser(1L, "admin", Roles.ADMIN));

        SysUser adminAcct = new SysUser();
        adminAcct.setId(2L);
        adminAcct.setUsername("root");
        adminAcct.setRole(Roles.ADMIN);
        adminAcct.setStatus(1);
        when(sysUserMapper.selectById(2L)).thenReturn(adminAcct);

        mockMvc.perform(get("/api/admin/users/2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void patchStatus_whenAdmin_disablesStudent_shouldReturnListRowShape() throws Exception {
        LoginUserHolder.set(new LoginUser(1L, "admin", Roles.ADMIN));

        SysUser before = new SysUser();
        before.setId(7L);
        before.setUsername("student7");
        before.setRole(Roles.USER);
        before.setStatus(1);
        before.setCreatedAt(LocalDateTime.parse("2026-02-01T08:30:00"));

        SysUser after = new SysUser();
        after.setId(7L);
        after.setUsername("student7");
        after.setRole(Roles.USER);
        after.setStatus(0);
        after.setCreatedAt(LocalDateTime.parse("2026-02-01T08:30:00"));

        when(sysUserMapper.selectById(7L)).thenReturn(before, after);
        when(userProfileMapper.selectById(7L)).thenReturn(null);

        mockMvc.perform(
                        patch("/api/admin/users/7")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"disabled\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("7"))
                .andExpect(jsonPath("$.data.status").value("disabled"));

        verify(sysUserMapper)
                .updateById(
                        argThat(
                                u ->
                                        u.getId() == 7L
                                                && u.getStatus() != null
                                                && u.getStatus() == 0));
    }

    @Test
    void putUpdate_whenAdminTriesToChangeRoleAwayFromUser_shouldReturn400ApiResponse() throws Exception {
        LoginUserHolder.set(new LoginUser(1L, "admin", Roles.ADMIN));

        SysUser u = new SysUser();
        u.setId(7L);
        u.setUsername("student7");
        u.setRole(Roles.USER);
        u.setStatus(1);
        when(sysUserMapper.selectById(7L)).thenReturn(u);

        mockMvc.perform(
                        put("/api/admin/users/7")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}

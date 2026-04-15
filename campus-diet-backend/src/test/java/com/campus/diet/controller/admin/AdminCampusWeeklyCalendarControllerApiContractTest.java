package com.campus.diet.controller.admin;

import com.campus.diet.common.GlobalExceptionHandler;
import com.campus.diet.security.LoginUser;
import com.campus.diet.security.LoginUserHolder;
import com.campus.diet.security.Roles;
import com.campus.diet.service.CampusWeeklyCalendarService;
import com.campus.diet.service.RuntimeMetricService;
import com.fasterxml.jackson.databind.JsonNode;
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

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端校园周历 API 契约（需 {@link Roles#canManageContent}；服务层 {@link CampusWeeklyCalendarService} 已 mock）。
 */
@ExtendWith(MockitoExtension.class)
class AdminCampusWeeklyCalendarControllerApiContractTest {

    private static final ObjectMapper OM = new ObjectMapper();

    @Mock
    private CampusWeeklyCalendarService campusWeeklyCalendarService;

    @Mock
    private RuntimeMetricService runtimeMetricService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new AdminCampusWeeklyCalendarController(campusWeeklyCalendarService))
                        .setControllerAdvice(new GlobalExceptionHandler(runtimeMetricService))
                        .build();
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    @Test
    void getOne_whenNotLoggedIn_shouldReturn401ApiResponse() throws Exception {
        mockMvc.perform(
                        get("/api/admin/campus-weekly-calendar")
                                .param("weekMonday", "2026-04-13")
                                .param("canteenId", "north"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void getOne_whenPlainUser_shouldReturn403ApiResponse() throws Exception {
        LoginUserHolder.set(new LoginUser(2L, "u", Roles.USER));
        mockMvc.perform(
                        get("/api/admin/campus-weekly-calendar")
                                .param("weekMonday", "2026-04-13")
                                .param("canteenId", "north"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void getOne_whenContentManager_shouldReturnDataEnvelope() throws Exception {
        LoginUserHolder.set(new LoginUser(3L, "cm", Roles.CANTEEN_MANAGER));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exists", false);
        payload.put("weekMonday", "2026-04-13");
        payload.put("canteenId", "north");
        payload.put("canteens", List.of());
        payload.put("published", false);
        payload.put("weekTitle", "契约周标题");
        payload.put("estimatedPublishNote", "");
        payload.put("days", List.of());
        payload.put("mealsTemplate", Map.of());
        when(campusWeeklyCalendarService.getAdminOne(eq(LocalDate.of(2026, 4, 13)), eq("north")))
                .thenReturn(payload);

        mockMvc.perform(
                        get("/api/admin/campus-weekly-calendar")
                                .param("weekMonday", "2026-04-13")
                                .param("canteenId", "north"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.exists").value(false))
                .andExpect(jsonPath("$.data.weekMonday").value("2026-04-13"))
                .andExpect(jsonPath("$.data.canteenId").value("north"))
                .andExpect(jsonPath("$.data.published").value(false))
                .andExpect(jsonPath("$.data.days").isArray())
                .andExpect(jsonPath("$.data.mealsTemplate").isMap());
    }

    @Test
    void getOne_whenWeekMondayInvalid_shouldReturn400ApiResponse() throws Exception {
        LoginUserHolder.set(new LoginUser(3L, "cm", Roles.CANTEEN_MANAGER));
        mockMvc.perform(
                        get("/api/admin/campus-weekly-calendar")
                                .param("weekMonday", "not-a-date")
                                .param("canteenId", "north"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("weekMonday 须为 yyyy-MM-dd"));
    }

    @Test
    void save_whenMissingWeekMondayOrCanteen_shouldReturn400ApiResponse() throws Exception {
        LoginUserHolder.set(new LoginUser(11L, "cm", Roles.CANTEEN_MANAGER));
        mockMvc.perform(
                        put("/api/admin/campus-weekly-calendar")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"days\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("缺少 weekMonday / canteenId"));
    }

    @Test
    void save_whenNeitherDaysNorMealsTemplate_shouldReturn400ApiResponse() throws Exception {
        LoginUserHolder.set(new LoginUser(11L, "cm", Roles.CANTEEN_MANAGER));
        mockMvc.perform(
                        put("/api/admin/campus-weekly-calendar")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                "{\"weekMonday\":\"2026-04-14\",\"canteenId\":\"c1\",\"published\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("请提供 mealsTemplate（周菜谱模板）或 days（完整周数据）"));
    }

    @Test
    void save_withMealsTemplateOnly_shouldCallSaveAdminWithTemplate() throws Exception {
        LoginUserHolder.set(new LoginUser(11L, "cm", Roles.CANTEEN_MANAGER));

        mockMvc.perform(
                        put("/api/admin/campus-weekly-calendar")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                "{\"weekMonday\":\"2026-04-14\",\"canteenId\":\"c1\",\"mealsTemplate\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(campusWeeklyCalendarService)
                .saveAdmin(
                        eq(LocalDate.of(2026, 4, 14)),
                        eq("c1"),
                        eq(false),
                        isNull(),
                        isNull(),
                        isNull(),
                        argThat(n -> n != null && n.isObject() && n.size() == 0),
                        eq(11L));
    }

    @Test
    void save_withDaysArrayOnly_shouldCallSaveAdminWithDays() throws Exception {
        LoginUserHolder.set(new LoginUser(11L, "cm", Roles.CANTEEN_MANAGER));

        mockMvc.perform(
                        put("/api/admin/campus-weekly-calendar")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                "{\"weekMonday\":\"2026-04-14\",\"canteenId\":\"c1\",\"days\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(campusWeeklyCalendarService)
                .saveAdmin(
                        eq(LocalDate.of(2026, 4, 14)),
                        eq("c1"),
                        eq(false),
                        isNull(),
                        isNull(),
                        argThat(n -> n != null && n.isArray() && n.size() == 0),
                        isNull(),
                        eq(11L));
    }

    @Test
    void save_withPublishedTextTrue_shouldPassPublishedTrue() throws Exception {
        LoginUserHolder.set(new LoginUser(11L, "cm", Roles.CANTEEN_MANAGER));

        mockMvc.perform(
                        put("/api/admin/campus-weekly-calendar")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                "{\"weekMonday\":\"2026-04-14\",\"canteenId\":\"c1\",\"published\":\"true\",\"days\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(campusWeeklyCalendarService)
                .saveAdmin(
                        eq(LocalDate.of(2026, 4, 14)),
                        eq("c1"),
                        eq(true),
                        isNull(),
                        isNull(),
                        argThat(JsonNode::isArray),
                        isNull(),
                        eq(11L));
    }
}

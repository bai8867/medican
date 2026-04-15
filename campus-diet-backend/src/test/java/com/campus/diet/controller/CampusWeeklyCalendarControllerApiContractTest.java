package com.campus.diet.controller;

import com.campus.diet.common.GlobalExceptionHandler;
import com.campus.diet.service.CampusWeeklyCalendarService;
import com.campus.diet.service.RuntimeMetricService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 校园周历公开 GET 契约样例（对齐 docs/api-contract.md 与前端 CampusWeeklyCalendar.vue）。
 */
@ExtendWith(MockitoExtension.class)
class CampusWeeklyCalendarControllerApiContractTest {

    @Mock
    private CampusWeeklyCalendarService campusWeeklyCalendarService;

    @Mock
    private RuntimeMetricService runtimeMetricService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new CampusWeeklyCalendarController(campusWeeklyCalendarService))
                        .setControllerAdvice(new GlobalExceptionHandler(runtimeMetricService))
                        .build();
    }

    @Test
    void weeklyCalendar_shouldReturnApiResponseWithCoreFields() throws Exception {
        Map<String, Object> c1 = new LinkedHashMap<>();
        c1.put("id", "north-1");
        c1.put("campusName", "本部");
        c1.put("name", "北区一食堂");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("published", true);
        payload.put("weekTitle", "契约示例周");
        payload.put("estimatedPublishNote", "");
        payload.put("canteens", List.of(c1));
        payload.put("days", List.of());
        when(campusWeeklyCalendarService.getPublicPayload(null)).thenReturn(payload);

        mockMvc.perform(get("/api/campus/weekly-calendar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.published").value(true))
                .andExpect(jsonPath("$.data.weekTitle").value("契约示例周"))
                .andExpect(jsonPath("$.data.canteens").isArray())
                .andExpect(jsonPath("$.data.canteens[0].id").value("north-1"))
                .andExpect(jsonPath("$.data.canteens[0].name").value("北区一食堂"))
                .andExpect(jsonPath("$.data.days").isArray());

        verify(campusWeeklyCalendarService).getPublicPayload(isNull());
    }

    @Test
    void weeklyCalendar_shouldForwardCanteenIdQuery() throws Exception {
        when(campusWeeklyCalendarService.getPublicPayload("east-2")).thenReturn(Map.of());

        mockMvc.perform(get("/api/campus/weekly-calendar").param("canteenId", "east-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(campusWeeklyCalendarService).getPublicPayload(eq("east-2"));
    }

    @Test
    void canteens_shouldReturnApiResponseListShape() throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "north-1");
        row.put("campusName", "");
        row.put("name", "北区");
        when(campusWeeklyCalendarService.listCanteenOptions()).thenReturn(List.of(row));

        mockMvc.perform(get("/api/campus/canteens"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value("north-1"))
                .andExpect(jsonPath("$.data[0].name").value("北区"));
    }
}

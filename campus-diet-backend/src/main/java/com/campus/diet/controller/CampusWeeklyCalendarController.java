package com.campus.diet.controller;

import com.campus.diet.common.ApiResponse;
import com.campus.diet.service.CampusWeeklyCalendarService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/campus")
public class CampusWeeklyCalendarController {

    private final CampusWeeklyCalendarService campusWeeklyCalendarService;

    public CampusWeeklyCalendarController(CampusWeeklyCalendarService campusWeeklyCalendarService) {
        this.campusWeeklyCalendarService = campusWeeklyCalendarService;
    }

    @GetMapping("/weekly-calendar")
    public ApiResponse<Map<String, Object>> weeklyCalendar(
            @RequestParam(name = "canteenId", required = false) String canteenId) {
        return ApiResponse.ok(campusWeeklyCalendarService.getPublicPayload(canteenId));
    }

    @GetMapping("/canteens")
    public ApiResponse<List<Map<String, Object>>> canteens() {
        return ApiResponse.ok(campusWeeklyCalendarService.listCanteenOptions());
    }
}

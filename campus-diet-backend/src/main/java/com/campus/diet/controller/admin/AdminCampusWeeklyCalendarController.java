package com.campus.diet.controller.admin;

import com.campus.diet.common.ApiResponse;
import com.campus.diet.common.BizException;
import com.campus.diet.security.LoginUser;
import com.campus.diet.security.SecurityUtils;
import com.campus.diet.service.CampusWeeklyCalendarService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/campus-weekly-calendar")
public class AdminCampusWeeklyCalendarController {

    private final CampusWeeklyCalendarService campusWeeklyCalendarService;

    public AdminCampusWeeklyCalendarController(CampusWeeklyCalendarService campusWeeklyCalendarService) {
        this.campusWeeklyCalendarService = campusWeeklyCalendarService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> getOne(
            @RequestParam("weekMonday") String weekMonday,
            @RequestParam("canteenId") String canteenId) {
        SecurityUtils.requireContentManager();
        try {
            LocalDate mon = LocalDate.parse(weekMonday.trim());
            return ApiResponse.ok(campusWeeklyCalendarService.getAdminOne(mon, canteenId));
        } catch (Exception e) {
            throw new BizException(400, "weekMonday 须为 yyyy-MM-dd");
        }
    }

    @PutMapping
    public ApiResponse<Void> save(@RequestBody JsonNode body) {
        SecurityUtils.requireContentManager();
        LoginUser u = SecurityUtils.requireLogin();
        if (body == null || !body.has("weekMonday") || !body.has("canteenId")) {
            throw new BizException(400, "缺少 weekMonday / canteenId");
        }
        JsonNode daysNode = body.path("days");
        JsonNode mealsTemplateNode = body.path("mealsTemplate");
        boolean hasDays = daysNode != null && daysNode.isArray();
        boolean hasMealsTemplate = mealsTemplateNode != null && mealsTemplateNode.isObject();
        if (!hasDays && !hasMealsTemplate) {
            throw new BizException(400, "请提供 mealsTemplate（周菜谱模板）或 days（完整周数据）");
        }
        try {
            LocalDate mon = LocalDate.parse(body.get("weekMonday").asText().trim());
            String canteenId = body.get("canteenId").asText().trim();
            boolean published = readPublished(body.get("published"));
            String weekTitle = body.has("weekTitle") && !body.get("weekTitle").isNull() ? body.get("weekTitle").asText() : null;
            String note =
                    body.has("estimatedPublishNote") && !body.get("estimatedPublishNote").isNull()
                            ? body.get("estimatedPublishNote").asText()
                            : null;
            JsonNode days = hasDays ? daysNode : null;
            JsonNode mealsTemplate = hasMealsTemplate ? mealsTemplateNode : null;
            campusWeeklyCalendarService.saveAdmin(mon, canteenId, published, weekTitle, note, days, mealsTemplate, u.getUserId());
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            throw new BizException(400, e.getMessage());
        } catch (Exception e) {
            throw new BizException(400, "保存失败：" + e.getMessage());
        }
    }

    private static boolean readPublished(JsonNode n) {
        if (n == null || n.isNull()) {
            return false;
        }
        if (n.isBoolean()) {
            return n.asBoolean();
        }
        if (n.isNumber()) {
            return n.asInt() != 0;
        }
        if (n.isTextual()) {
            String t = n.asText().trim();
            return "1".equals(t) || "true".equalsIgnoreCase(t);
        }
        return false;
    }
}

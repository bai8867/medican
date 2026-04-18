package com.campus.diet.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.diet.entity.CampusCanteen;
import com.campus.diet.entity.CampusWeeklyCalendar;
import com.campus.diet.mapper.CampusCanteenMapper;
import com.campus.diet.mapper.CampusWeeklyCalendarMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CampusWeeklyCalendarService {

    public static final String CANTEEN_NORTH = "north-1";
    public static final String CANTEEN_EAST = "east-2";
    public static final String CANTEEN_SOUTH_UNPUB = "south-unpub";

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String[] WD_LABELS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private static final String[] MEAL_KEYS = {"breakfast", "lunch", "dinner", "midnightSnack"};

    private final CampusCanteenMapper campusCanteenMapper;
    private final CampusWeeklyCalendarMapper campusWeeklyCalendarMapper;
    private final CampusWeeklyCalendarRecipeEnricher recipeEnricher;
    private final ObjectMapper objectMapper;

    public CampusWeeklyCalendarService(
            CampusCanteenMapper campusCanteenMapper,
            CampusWeeklyCalendarMapper campusWeeklyCalendarMapper,
            CampusWeeklyCalendarRecipeEnricher recipeEnricher,
            ObjectMapper objectMapper) {
        this.campusCanteenMapper = campusCanteenMapper;
        this.campusWeeklyCalendarMapper = campusWeeklyCalendarMapper;
        this.recipeEnricher = recipeEnricher;
        this.objectMapper = objectMapper;
    }

    public static LocalDate mondayOf(LocalDate anchor) {
        return anchor.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
    }

    public List<Map<String, Object>> listCanteenOptions() {
        List<CampusCanteen> rows =
                campusCanteenMapper.selectList(Wrappers.<CampusCanteen>lambdaQuery().orderByAsc(CampusCanteen::getSortOrder));
        List<Map<String, Object>> out = new ArrayList<>();
        for (CampusCanteen c : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("campusName", c.getCampusName() == null ? "" : c.getCampusName());
            m.put("name", c.getDisplayName());
            out.add(m);
        }
        return out;
    }

    public Map<String, Object> getPublicPayload(String canteenId) {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate monday = mondayOf(today);
        List<Map<String, Object>> canteens = listCanteenOptions();
        String cid = canteenId == null || canteenId.isBlank() ? CANTEEN_NORTH : canteenId.trim();

        CampusWeeklyCalendar row = campusWeeklyCalendarMapper.selectOne(
                Wrappers.<CampusWeeklyCalendar>lambdaQuery()
                        .eq(CampusWeeklyCalendar::getWeekMonday, monday)
                        .eq(CampusWeeklyCalendar::getCanteenId, cid));

        if (row == null) {
            return emptyUnpublished(monday, canteens, "本周日历正在筹备中，请稍后再试。");
        }

        boolean published = row.getPublished() != null && row.getPublished() == 1;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("published", published);
        out.put("weekTitle", row.getWeekTitle() != null && !row.getWeekTitle().isBlank()
                ? row.getWeekTitle()
                : buildWeekTitle(monday));
        out.put("estimatedPublishNote", row.getEstimatedPublishNote());
        out.put("canteens", canteens);

        if (!published) {
            out.put("days", List.of());
            return out;
        }

        try {
            JsonNode days = objectMapper.readTree(row.getDaysJson());
            if (!days.isArray()) {
                out.put("days", List.of());
                return out;
            }
            String todayStr = today.toString();
            ArrayNode enriched = objectMapper.createArrayNode();
            for (JsonNode d : days) {
                ObjectNode copy = (ObjectNode) objectMapper.readTree(d.toString());
                if (copy.has("date") && todayStr.equals(copy.get("date").asText())) {
                    copy.put("wellnessBanner", "今日宜：滋阴润肺 · 忌：辛辣油腻");
                }
                enriched.add(copy);
            }
            recipeEnricher.enrichDaysDishesFromRecipeTable(enriched);
            out.put("days", objectMapper.convertValue(enriched, new TypeReference<List<Object>>() {}));
        } catch (Exception e) {
            out.put("days", List.of());
        }
        return out;
    }

    public Map<String, Object> getAdminOne(LocalDate weekMonday, String canteenId) {
        if (weekMonday == null || canteenId == null || canteenId.isBlank()) {
            throw new IllegalArgumentException("weekMonday 与 canteenId 不能为空");
        }
        CampusWeeklyCalendar row = campusWeeklyCalendarMapper.selectOne(
                Wrappers.<CampusWeeklyCalendar>lambdaQuery()
                        .eq(CampusWeeklyCalendar::getWeekMonday, weekMonday)
                        .eq(CampusWeeklyCalendar::getCanteenId, canteenId.trim()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("canteens", listCanteenOptions());
        if (row == null) {
            out.put("weekMonday", weekMonday.toString());
            out.put("canteenId", canteenId.trim());
            out.put("exists", false);
            out.put("published", false);
            out.put("weekTitle", buildWeekTitle(weekMonday));
            out.put("estimatedPublishNote", "");
            out.put("days", List.of());
            out.put("mealsTemplate", emptyMealsTemplateNode());
            return out;
        }
        out.put("exists", true);
        out.put("weekMonday", row.getWeekMonday().toString());
        out.put("canteenId", row.getCanteenId());
        out.put("published", row.getPublished() != null && row.getPublished() == 1);
        out.put("weekTitle", row.getWeekTitle());
        out.put("estimatedPublishNote", row.getEstimatedPublishNote());
        try {
            List<Object> daysList =
                    objectMapper.readValue(row.getDaysJson(), new TypeReference<List<Object>>() {});
            out.put("days", daysList);
            out.put("mealsTemplate", mealsTemplateFromDaysJson(row.getDaysJson()));
        } catch (Exception e) {
            out.put("days", List.of());
            out.put("mealsTemplate", emptyMealsTemplateNode());
        }
        return out;
    }

    /**
     * 管理端「周菜谱模板」：与每日 meals 结构一致（无菜品 id），保存时由服务端按周一生成 7 日 days_json。
     */
    public JsonNode mealsTemplateFromDaysJson(String daysJsonStr) {
        try {
            if (daysJsonStr == null || daysJsonStr.isBlank()) {
                return emptyMealsTemplateNode();
            }
            JsonNode days = objectMapper.readTree(daysJsonStr);
            if (!days.isArray() || days.size() == 0 || !days.get(0).isObject()) {
                return emptyMealsTemplateNode();
            }
            JsonNode first = days.get(0);
            if (!first.has("meals") || !first.get("meals").isObject()) {
                return emptyMealsTemplateNode();
            }
            return stripDishIdsFromMeals(first.get("meals"));
        } catch (Exception e) {
            return emptyMealsTemplateNode();
        }
    }

    private JsonNode emptyMealsTemplateNode() {
        ObjectNode o = objectMapper.createObjectNode();
        for (String key : MEAL_KEYS) {
            o.set(key, objectMapper.createArrayNode());
        }
        return o;
    }

    private JsonNode stripDishIdsFromMeals(JsonNode meals) throws JsonProcessingException {
        ObjectNode copy = (ObjectNode) objectMapper.readTree(meals.toString());
        for (String key : MEAL_KEYS) {
            if (!copy.has(key) || !copy.get(key).isArray()) {
                copy.set(key, objectMapper.createArrayNode());
                continue;
            }
            ArrayNode src = (ArrayNode) copy.get(key);
            ArrayNode out = objectMapper.createArrayNode();
            for (JsonNode d : src) {
                if (!d.isObject()) {
                    continue;
                }
                ObjectNode dish = (ObjectNode) objectMapper.readTree(d.toString());
                dish.remove("id");
                out.add(dish);
            }
            copy.set(key, out);
        }
        return copy;
    }

    @Transactional
    public void saveAdmin(
            LocalDate weekMonday,
            String canteenId,
            boolean published,
            String weekTitle,
            String estimatedPublishNote,
            JsonNode daysNode,
            JsonNode mealsTemplateNode,
            Long updatedBy)
            throws JsonProcessingException {
        if (weekMonday == null || canteenId == null || canteenId.isBlank()) {
            throw new IllegalArgumentException("weekMonday 与 canteenId 不能为空");
        }
        String cid = canteenId.trim();
        Long canteenCount = campusCanteenMapper.selectCount(Wrappers.<CampusCanteen>lambdaQuery().eq(CampusCanteen::getId, cid));
        if (canteenCount == null || canteenCount == 0) {
            throw new IllegalArgumentException("食堂不存在: " + cid);
        }
        JsonNode resolvedDays;
        if (mealsTemplateNode != null && mealsTemplateNode.isObject()) {
            resolvedDays = buildDaysArray(mealsTemplateNode, weekMonday, LocalDate.now(ZONE));
        } else if (daysNode != null && daysNode.isArray()) {
            resolvedDays = daysNode;
        } else {
            throw new IllegalArgumentException("请提供 mealsTemplate（周菜谱模板）或 days（完整周 JSON 数组）");
        }

        CampusWeeklyCalendar row = campusWeeklyCalendarMapper.selectOne(
                Wrappers.<CampusWeeklyCalendar>lambdaQuery()
                        .eq(CampusWeeklyCalendar::getWeekMonday, weekMonday)
                        .eq(CampusWeeklyCalendar::getCanteenId, cid));

        String daysJson = objectMapper.writeValueAsString(resolvedDays);
        if (row == null) {
            CampusWeeklyCalendar n = new CampusWeeklyCalendar();
            n.setWeekMonday(weekMonday);
            n.setCanteenId(cid);
            n.setPublished(published ? 1 : 0);
            n.setWeekTitle(blankToNull(weekTitle));
            n.setEstimatedPublishNote(blankToNull(estimatedPublishNote));
            n.setDaysJson(daysJson);
            n.setUpdatedBy(updatedBy);
            campusWeeklyCalendarMapper.insert(n);
        } else {
            row.setPublished(published ? 1 : 0);
            row.setWeekTitle(blankToNull(weekTitle));
            row.setEstimatedPublishNote(blankToNull(estimatedPublishNote));
            row.setDaysJson(daysJson);
            row.setUpdatedBy(updatedBy);
            campusWeeklyCalendarMapper.updateById(row);
        }
    }

    /** 启动时：本周尚无记录则写入与前端 Mock 对齐的演示数据 */
    @Transactional
    public void seedCurrentWeekIfEmpty(boolean enabled) throws Exception {
        if (!enabled) {
            return;
        }
        LocalDate today = LocalDate.now(ZONE);
        LocalDate monday = mondayOf(today);
        seedSouthUnpublishedIfEmpty(monday);
        seedNorthIfEmpty(monday, today);
        seedEastIfEmpty(monday, today);
    }

    private void seedSouthUnpublishedIfEmpty(LocalDate monday) throws Exception {
        Long n = campusWeeklyCalendarMapper.selectCount(
                Wrappers.<CampusWeeklyCalendar>lambdaQuery()
                        .eq(CampusWeeklyCalendar::getWeekMonday, monday)
                        .eq(CampusWeeklyCalendar::getCanteenId, CANTEEN_SOUTH_UNPUB));
        if (n != null && n > 0) {
            return;
        }
        CampusWeeklyCalendar row = new CampusWeeklyCalendar();
        row.setWeekMonday(monday);
        row.setCanteenId(CANTEEN_SOUTH_UNPUB);
        row.setPublished(0);
        row.setWeekTitle("本周药膳日历");
        row.setEstimatedPublishNote("本周菜单预计每周日 20:00 发布，届时将同步更新小程序与网页端。");
        row.setDaysJson("[]");
        campusWeeklyCalendarMapper.insert(row);
    }

    private void seedNorthIfEmpty(LocalDate monday, LocalDate today) throws Exception {
        Long n = campusWeeklyCalendarMapper.selectCount(
                Wrappers.<CampusWeeklyCalendar>lambdaQuery()
                        .eq(CampusWeeklyCalendar::getWeekMonday, monday)
                        .eq(CampusWeeklyCalendar::getCanteenId, CANTEEN_NORTH));
        if (n != null && n > 0) {
            return;
        }
        JsonNode mealsTemplate = loadNorthMealsTemplate();
        ArrayNode days = buildDaysArray(mealsTemplate, monday, today);
        insertCalendarRow(monday, CANTEEN_NORTH, false, buildWeekTitle(monday), null, days);
    }

    private void seedEastIfEmpty(LocalDate monday, LocalDate today) throws Exception {
        Long n = campusWeeklyCalendarMapper.selectCount(
                Wrappers.<CampusWeeklyCalendar>lambdaQuery()
                        .eq(CampusWeeklyCalendar::getWeekMonday, monday)
                        .eq(CampusWeeklyCalendar::getCanteenId, CANTEEN_EAST));
        if (n != null && n > 0) {
            return;
        }
        JsonNode eastMeals = buildEastMealsFromNorth(loadNorthMealsTemplate());
        ArrayNode days = buildDaysArray(eastMeals, monday, today);
        insertCalendarRow(monday, CANTEEN_EAST, false, buildWeekTitle(monday), null, days);
    }

    private void insertCalendarRow(
            LocalDate monday,
            String canteenId,
            boolean published,
            String weekTitle,
            String note,
            ArrayNode days) throws Exception {
        CampusWeeklyCalendar row = new CampusWeeklyCalendar();
        row.setWeekMonday(monday);
        row.setCanteenId(canteenId);
        row.setPublished(published ? 1 : 0);
        row.setWeekTitle(weekTitle);
        row.setEstimatedPublishNote(note);
        row.setDaysJson(objectMapper.writeValueAsString(days));
        campusWeeklyCalendarMapper.insert(row);
    }

    private JsonNode loadNorthMealsTemplate() throws Exception {
        InputStream raw = getClass().getResourceAsStream("/bootstrap/weekly-calendar-meals-north1.json");
        if (raw == null) {
            throw new IllegalStateException("缺少 classpath:bootstrap/weekly-calendar-meals-north1.json");
        }
        try (InputStream in = raw) {
            return objectMapper.readTree(in);
        }
    }

    private JsonNode buildEastMealsFromNorth(JsonNode north) throws Exception {
        JsonNode copy = objectMapper.readTree(north.toString());
        ObjectNode meals = (ObjectNode) copy;
        for (String key : MEAL_KEYS) {
            if (!meals.has(key) || !meals.get(key).isArray()) {
                continue;
            }
            ArrayNode arr = (ArrayNode) meals.get(key);
            for (int i = 0; i < arr.size(); i++) {
                if (!arr.get(i).isObject()) {
                    continue;
                }
                ObjectNode dish = (ObjectNode) arr.get(i);
                if (dish.has("window") && dish.get("window").isTextual()) {
                    String w = dish.get("window").asText().replace("二楼", "东苑").replace("一楼", "东苑");
                    dish.put("window", w);
                }
                if (dish.has("priceYuan") && dish.get("priceYuan").isNumber()) {
                    int base = dish.get("priceYuan").asInt();
                    dish.put("priceYuan", Math.max(5, base + i - 1));
                }
            }
        }
        return meals;
    }

    private ArrayNode buildDaysArray(JsonNode mealsTemplate, LocalDate monday, LocalDate today)
            throws JsonProcessingException {
        ArrayNode days = objectMapper.createArrayNode();
        for (int i = 0; i < 7; i++) {
            LocalDate d = monday.plusDays(i);
            JsonNode mealsForDay = objectMapper.readTree(mealsTemplate.toString());
            applyTodayStopOnHuangqi((ObjectNode) mealsForDay, d, today);
            assignDishIds(d.toString(), (ObjectNode) mealsForDay);
            ObjectNode day = objectMapper.createObjectNode();
            day.put("date", d.toString());
            day.put("weekdayLabel", WD_LABELS[i]);
            day.put("weekdayIndex", i);
            day.set("meals", mealsForDay);
            days.add(day);
        }
        return days;
    }

    private void applyTodayStopOnHuangqi(ObjectNode meals, LocalDate dayDate, LocalDate today) {
        if (!meals.has("lunch") || !meals.get("lunch").isArray() || meals.get("lunch").size() == 0) {
            return;
        }
        ObjectNode first = (ObjectNode) meals.get("lunch").get(0);
        boolean isToday = dayDate.equals(today);
        first.put("stopped", isToday);
        if (isToday) {
            first.put("stopReason", "当日食材黄芪临时缺货，已暂停供应，预计次日恢复。");
        } else {
            first.remove("stopReason");
        }
    }

    private void assignDishIds(String ymd, ObjectNode meals) {
        for (String meal : MEAL_KEYS) {
            if (!meals.has(meal) || !meals.get(meal).isArray()) {
                continue;
            }
            ArrayNode arr = (ArrayNode) meals.get(meal);
            String pfx = mealPrefix(meal);
            for (int i = 0; i < arr.size(); i++) {
                if (arr.get(i).isObject()) {
                    ((ObjectNode) arr.get(i)).put("id", pfx + ymd + "-" + (i + 1));
                }
            }
        }
    }

    private static String mealPrefix(String meal) {
        switch (meal) {
            case "breakfast":
                return "bf-";
            case "lunch":
                return "lc-";
            case "dinner":
                return "dn-";
            case "midnightSnack":
                return "ms-";
            default:
                return "d-";
        }
    }

    private static String buildWeekTitle(LocalDate mon) {
        LocalDate sun = mon.plusDays(6);
        return mon.getMonthValue() + "月" + mon.getDayOfMonth() + "日 — " + sun.getMonthValue() + "月" + sun.getDayOfMonth() + "日";
    }

    private Map<String, Object> emptyUnpublished(LocalDate monday, List<Map<String, Object>> canteens, String note) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("published", false);
        out.put("weekTitle", buildWeekTitle(monday));
        out.put("estimatedPublishNote", note);
        out.put("canteens", canteens);
        out.put("days", List.of());
        return out;
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

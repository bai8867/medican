package com.campus.diet.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.diet.common.ApiResponse;
import com.campus.diet.entity.Recipe;
import com.campus.diet.mapper.RecipeMapper;
import com.campus.diet.mapper.SysUserMapper;
import com.campus.diet.mapper.UserProfileMapper;
import com.campus.diet.security.SecurityUtils;
import com.campus.diet.service.RecipeRecommendService;
import com.campus.diet.service.RuntimeMetricService;
import com.campus.diet.service.SeasonUtil;
import com.campus.diet.util.ConstitutionLabelUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final RecipeMapper recipeMapper;
    private final RecipeRecommendService recipeRecommendService;
    private final SysUserMapper sysUserMapper;
    private final UserProfileMapper userProfileMapper;
    private final RuntimeMetricService runtimeMetricService;

    public AdminDashboardController(
            RecipeMapper recipeMapper,
            RecipeRecommendService recipeRecommendService,
            SysUserMapper sysUserMapper,
            UserProfileMapper userProfileMapper,
            RuntimeMetricService runtimeMetricService) {
        this.recipeMapper = recipeMapper;
        this.recipeRecommendService = recipeRecommendService;
        this.sysUserMapper = sysUserMapper;
        this.userProfileMapper = userProfileMapper;
        this.runtimeMetricService = runtimeMetricService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> dashboard() {
        SecurityUtils.requireContentManager();
        List<Recipe> topCollected = recipeMapper.selectList(
                Wrappers.<Recipe>lambdaQuery().eq(Recipe::getStatus, 1).orderByDesc(Recipe::getCollectCount).last("LIMIT 10"));
        String season = SeasonUtil.currentSeasonCode(LocalDate.now());
        List<Recipe> seasonal = recipeRecommendService.recommendFeed(1, 10, null, "", false, season, null);
        Map<String, Object> m = new HashMap<>();
        m.put("topCollected", topCollected);
        m.put("seasonalPicks", seasonal);
        m.put("seasonCode", season);
        return ApiResponse.ok(m);
    }

    /**
     * 管理端看板指标：全站收藏合计、热门 TOP3、注册用户总数。
     */
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        SecurityUtils.requireContentManager();
        QueryWrapper<Recipe> sumQw = Wrappers.query();
        sumQw.select("IFNULL(SUM(collect_count),0) AS totalCollect");
        sumQw.eq("status", 1);
        List<Map<String, Object>> sumRows = recipeMapper.selectMaps(sumQw);
        long totalCollect = 0;
        if (!sumRows.isEmpty() && sumRows.get(0).get("totalCollect") != null) {
            totalCollect = ((Number) sumRows.get(0).get("totalCollect")).longValue();
        }
        List<Recipe> top3 = recipeMapper.selectList(
                Wrappers.<Recipe>lambdaQuery().eq(Recipe::getStatus, 1).orderByDesc(Recipe::getCollectCount).last("LIMIT 3"));
        List<Map<String, Object>> hotTop3 = new ArrayList<>();
        for (Recipe r : top3) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", r.getId());
            row.put("name", r.getName());
            row.put("collectCount", r.getCollectCount() == null ? 0 : r.getCollectCount());
            hotTop3.add(row);
        }
        long userTotal = sysUserMapper.selectCount(Wrappers.emptyWrapper());
        Map<String, Object> m = new HashMap<>();
        m.put("totalCollectCount", totalCollect);
        m.put("hotTop3", hotTop3);
        m.put("userTotal", userTotal);
        return ApiResponse.ok(m);
    }

    /**
     * 用户表体质字段分布（饼图）。
     */
    @GetMapping("/constitution-distribution")
    public ApiResponse<Map<String, Object>> constitutionDistribution() {
        SecurityUtils.requireContentManager();
        Map<String, Integer> byLabel = new LinkedHashMap<>();
        List<Map<String, Object>> rows = userProfileMapper.selectMaps(
                Wrappers.<com.campus.diet.entity.UserProfile>query()
                        .select("constitution_code AS constitutionCode", "COUNT(1) AS cnt")
                        .groupBy("constitution_code"));
        for (Map<String, Object> row : rows) {
            String code = String.valueOf(row.getOrDefault("constitutionCode", "")).trim();
            int cnt = row.get("cnt") instanceof Number ? ((Number) row.get("cnt")).intValue() : 0;
            if (cnt <= 0) {
                continue;
            }
            String label;
            if (code.isEmpty() || "null".equalsIgnoreCase(code)) {
                label = "未设置";
            } else {
                String mapped = ConstitutionLabelUtil.labelForCode(code);
                label = (mapped == null || mapped.isEmpty()) ? code : mapped;
            }
            byLabel.merge(label, cnt, Integer::sum);
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map.Entry<String, Integer> e : byLabel.entrySet()) {
            Map<String, Object> row = new HashMap<>();
            row.put("name", e.getKey());
            row.put("value", e.getValue());
            items.add(row);
        }
        Map<String, Object> m = new HashMap<>();
        m.put("items", items);
        return ApiResponse.ok(m);
    }

    @GetMapping("/runtime-metrics")
    public ApiResponse<Map<String, Object>> runtimeMetrics() {
        SecurityUtils.requireContentManager();
        return ApiResponse.ok(runtimeMetricService.snapshot());
    }

    /**
     * 最小可观测摘要：在 {@link #runtimeMetrics()} 原始快照上派生比率，便于管理端或外部探针拉取（非 Prometheus 格式）。
     */
    @GetMapping("/observability-summary")
    public ApiResponse<Map<String, Object>> observabilitySummary() {
        SecurityUtils.requireContentManager();
        return ApiResponse.ok(buildObservabilitySummary(runtimeMetricService.snapshot()));
    }

    static Map<String, Object> buildObservabilitySummary(Map<String, Object> snapshot) {
        Map<String, Long> counters = longMap(snapshot.get("counters"));
        long httpTotal = nz(counters.get("http.request.total"));
        long errNone = nz(counters.get("http.request.error.none"));
        long errServer = nz(counters.get("http.request.error.server"));
        long errClient = nz(counters.get("http.request.error.client"));
        long errAuth = nz(counters.get("http.request.error.auth"));
        long errValidation = nz(counters.get("http.request.error.validation"));
        long errNumerator = errServer + errClient + errAuth + errValidation;
        Map<String, Long> avg = longMap(snapshot.get("avgCostMs"));
        Map<String, Long> max = longMap(snapshot.get("maxCostMs"));

        long recNonEmpty = nz(counters.get("recommend.feed.page_nonempty"));
        long recEmpty = nz(counters.get("recommend.feed.page_empty"));
        long recDenom = recNonEmpty + recEmpty;
        Double recNonemptyRatio = recDenom <= 0 ? null : (double) recNonEmpty / (double) recDenom;

        long aiTherapyFail = nz(counters.get("ai.generate.upstream.failed"));
        long aiTherapyOk = nz(counters.get("ai.generate.upstream.success"));
        long aiTherapyDenom = aiTherapyFail + aiTherapyOk;
        Double aiTherapyUpstreamOkRatio = aiTherapyDenom <= 0 ? null : (double) aiTherapyOk / (double) aiTherapyDenom;

        long aiDietFail = nz(counters.get("ai.diet.upstream.failed"));
        long aiDietOk = nz(counters.get("ai.diet.upstream.success"));
        long aiDietDenom = aiDietFail + aiDietOk;
        Double aiDietUpstreamOkRatio = aiDietDenom <= 0 ? null : (double) aiDietOk / (double) aiDietDenom;

        long therapyBudgetObs = nz(counters.get("ai.generate.therapy.prompt_budget.observed"));
        long therapyBudgetChars = nz(counters.get("ai.generate.therapy.prompt_budget.chars_total"));
        Double therapyAvgPromptUtf16 =
                therapyBudgetObs <= 0 ? null : (double) therapyBudgetChars / (double) therapyBudgetObs;
        long dietBudgetObs = nz(counters.get("ai.diet.prompt_budget.observed"));
        long dietBudgetChars = nz(counters.get("ai.diet.prompt_budget.chars_total"));
        Double dietAvgPromptUtf16 = dietBudgetObs <= 0 ? null : (double) dietBudgetChars / (double) dietBudgetObs;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("http.request.total", httpTotal);
        out.put("http.error_classified", errNumerator);
        out.put("http.success_observed", errNone);
        out.put(
                "http.error_rate_classified",
                httpTotal <= 0 ? null : (double) errNumerator / (double) httpTotal);
        out.put("http.avg_cost_ms", avg.get("http.request.cost"));
        out.put("http.max_cost_ms", max.get("http.request.cost"));
        out.put("recommend.feed.page_nonempty", recNonEmpty);
        out.put("recommend.feed.page_empty", recEmpty);
        out.put("recommend.feed.nonempty_ratio", recNonemptyRatio);
        out.put("ai.therapy.upstream_ok_ratio", aiTherapyUpstreamOkRatio);
        out.put("ai.diet.upstream_ok_ratio", aiDietUpstreamOkRatio);
        out.put("recommend.avg_cost_ms", avg.get("recommend.request"));
        out.put("recommend.max_cost_ms", max.get("recommend.request"));
        out.put("ai.generate.avg_cost_ms", avg.get("ai.generate"));
        out.put("ai.generate.max_cost_ms", max.get("ai.generate"));
        out.put("ai.therapy.prompt_budget.observed", therapyBudgetObs);
        out.put("ai.therapy.prompt_budget.chars_total", therapyBudgetChars);
        out.put("ai.therapy.prompt_budget.avg_utf16_content_units", therapyAvgPromptUtf16);
        out.put("ai.diet.prompt_budget.observed", dietBudgetObs);
        out.put("ai.diet.prompt_budget.chars_total", dietBudgetChars);
        out.put("ai.diet.prompt_budget.avg_utf16_content_units", dietAvgPromptUtf16);
        return out;
    }

    private static Map<String, Long> longMap(Object raw) {
        if (!(raw instanceof Map)) {
            return Map.of();
        }
        Map<?, ?> m = (Map<?, ?>) raw;
        Map<String, Long> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (e.getKey() == null || !(e.getValue() instanceof Number)) {
                continue;
            }
            out.put(String.valueOf(e.getKey()), ((Number) e.getValue()).longValue());
        }
        return out;
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }
}

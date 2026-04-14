package com.campus.diet.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.diet.common.ApiResponse;
import com.campus.diet.entity.Recipe;
import com.campus.diet.entity.UserProfile;
import com.campus.diet.mapper.RecipeMapper;
import com.campus.diet.mapper.SysUserMapper;
import com.campus.diet.mapper.UserProfileMapper;
import com.campus.diet.security.SecurityUtils;
import com.campus.diet.service.RecipeRecommendService;
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

    public AdminDashboardController(
            RecipeMapper recipeMapper,
            RecipeRecommendService recipeRecommendService,
            SysUserMapper sysUserMapper,
            UserProfileMapper userProfileMapper) {
        this.recipeMapper = recipeMapper;
        this.recipeRecommendService = recipeRecommendService;
        this.sysUserMapper = sysUserMapper;
        this.userProfileMapper = userProfileMapper;
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
        List<UserProfile> profiles = userProfileMapper.selectList(Wrappers.emptyWrapper());
        Map<String, Integer> byLabel = new LinkedHashMap<>();
        int unset = 0;
        for (UserProfile p : profiles) {
            String code = p.getConstitutionCode();
            if (code == null || code.isBlank()) {
                unset++;
                continue;
            }
            String label = ConstitutionLabelUtil.labelForCode(code);
            if (label == null || label.isEmpty()) {
                label = code.trim();
            }
            byLabel.merge(label, 1, Integer::sum);
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map.Entry<String, Integer> e : byLabel.entrySet()) {
            Map<String, Object> row = new HashMap<>();
            row.put("name", e.getKey());
            row.put("value", e.getValue());
            items.add(row);
        }
        if (unset > 0) {
            Map<String, Object> row = new HashMap<>();
            row.put("name", "未设置");
            row.put("value", unset);
            items.add(row);
        }
        Map<String, Object> m = new HashMap<>();
        m.put("items", items);
        return ApiResponse.ok(m);
    }
}

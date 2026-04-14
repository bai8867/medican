package com.campus.diet.controller;

import com.campus.diet.common.ApiResponse;
import com.campus.diet.common.BizException;
import com.campus.diet.common.PageResult;
import com.campus.diet.dto.RecipeCardDto;
import com.campus.diet.entity.Recipe;
import com.campus.diet.security.LoginUser;
import com.campus.diet.security.SecurityUtils;
import com.campus.diet.service.BrowseHistoryService;
import com.campus.diet.service.FavoriteService;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user")
public class FavoriteHistoryController {

    private final FavoriteService favoriteService;
    private final BrowseHistoryService browseHistoryService;

    public FavoriteHistoryController(FavoriteService favoriteService, BrowseHistoryService browseHistoryService) {
        this.favoriteService = favoriteService;
        this.browseHistoryService = browseHistoryService;
    }

    @PostMapping("/favorites")
    public ApiResponse<Map<String, Object>> addFavorite(@RequestBody IdBody body) {
        LoginUser u = SecurityUtils.requireLogin();
        long rid = parseRecipeId(body.getRecipeId());
        favoriteService.add(u.getUserId(), rid);
        Map<String, Object> m = new HashMap<>();
        m.put("ok", true);
        return ApiResponse.ok(m);
    }

    @DeleteMapping("/favorites/{recipeId}")
    public ApiResponse<Map<String, Object>> removeFavorite(@PathVariable String recipeId) {
        LoginUser u = SecurityUtils.requireLogin();
        favoriteService.remove(u.getUserId(), parseRecipeId(recipeId));
        Map<String, Object> m = new HashMap<>();
        m.put("ok", true);
        return ApiResponse.ok(m);
    }

    @GetMapping("/favorites")
    public ApiResponse<PageResult<RecipeCardDto>> listFavorites(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "10") int pageSize) {
        LoginUser u = SecurityUtils.requireLogin();
        PageResult<Recipe> pr = favoriteService.page(u.getUserId(), page, pageSize);
        List<RecipeCardDto> records = pr.getRecords().stream().map(RecipeCardDto::from).collect(Collectors.toList());
        return ApiResponse.ok(new PageResult<>(records, pr.getTotal(), pr.getPage(), pr.getPageSize(), pr.isHasMore()));
    }

    @PostMapping("/history")
    public ApiResponse<Map<String, Object>> addHistory(@RequestBody IdBody body) {
        LoginUser u = SecurityUtils.requireLogin();
        long rid = parseRecipeId(body.getRecipeId());
        browseHistoryService.record(u.getUserId(), rid);
        Map<String, Object> m = new HashMap<>();
        m.put("ok", true);
        return ApiResponse.ok(m);
    }

    @GetMapping("/history")
    public ApiResponse<Map<String, Object>> listHistory(@RequestParam(defaultValue = "10") int limit) {
        LoginUser u = SecurityUtils.requireLogin();
        List<Map<String, Object>> records = browseHistoryService.listHistoryRecords(u.getUserId(), limit);
        Map<String, Object> m = new HashMap<>();
        m.put("records", records);
        return ApiResponse.ok(m);
    }

    @DeleteMapping("/history/{historyId}")
    public ApiResponse<Map<String, Object>> deleteHistory(@PathVariable String historyId) {
        LoginUser u = SecurityUtils.requireLogin();
        long hid;
        try {
            hid = Long.parseLong(historyId.trim());
        } catch (NumberFormatException e) {
            throw new BizException(400, "非法 historyId");
        }
        browseHistoryService.deleteHistoryRow(u.getUserId(), hid);
        Map<String, Object> m = new HashMap<>();
        m.put("ok", true);
        return ApiResponse.ok(m);
    }

    @DeleteMapping("/history")
    public ApiResponse<Map<String, Object>> clearHistory() {
        LoginUser u = SecurityUtils.requireLogin();
        browseHistoryService.clearHistory(u.getUserId());
        Map<String, Object> m = new HashMap<>();
        m.put("ok", true);
        return ApiResponse.ok(m);
    }

    private static long parseRecipeId(Object raw) {
        if (raw == null) {
            throw new BizException(400, "缺少 recipeId");
        }
        if (raw instanceof Number) {
            return ((Number) raw).longValue();
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) {
            throw new BizException(400, "缺少 recipeId");
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new BizException(400, "recipeId 须为服务端数字 ID（对接列表返回的 id）");
        }
    }

    @Data
    public static class IdBody {
        /** 兼容联调脚本与部分客户端使用的 snake_case */
        @JsonAlias("recipe_id")
        private Object recipeId;
    }
}

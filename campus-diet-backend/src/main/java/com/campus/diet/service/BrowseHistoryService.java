package com.campus.diet.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.diet.entity.BrowseHistory;
import com.campus.diet.entity.Recipe;
import com.campus.diet.mapper.BrowseHistoryMapper;
import com.campus.diet.mapper.RecipeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BrowseHistoryService {

    /** 每个用户最多保留的不同药膳条数（同一药膳仅一条，再次浏览会更新时间并顶到最前） */
    public static final int MAX_BROWSE_HISTORY = 10;

    private final BrowseHistoryMapper browseHistoryMapper;
    private final RecipeMapper recipeMapper;

    public BrowseHistoryService(BrowseHistoryMapper browseHistoryMapper, RecipeMapper recipeMapper) {
        this.browseHistoryMapper = browseHistoryMapper;
        this.recipeMapper = recipeMapper;
    }

    @Transactional
    public void record(long userId, long recipeId) {
        // 同一用户 + 同一药膳只保留一条：先删旧行（兼容历史重复数据），再插入最新浏览时间
        browseHistoryMapper.delete(Wrappers.<BrowseHistory>lambdaQuery()
                .eq(BrowseHistory::getUserId, userId)
                .eq(BrowseHistory::getRecipeId, recipeId));
        BrowseHistory h = new BrowseHistory();
        h.setUserId(userId);
        h.setRecipeId(recipeId);
        h.setViewedAt(LocalDateTime.now());
        browseHistoryMapper.insert(h);
        pruneExcessRows(userId);
    }

    /**
     * 该用户浏览行数超过 {@link #MAX_BROWSE_HISTORY} 时，按浏览时间、主键升序删除最旧行
     * （在 {@link #record} 已保证每药膳一行，故即「最多保留十个药膳」）。
     */
    private void pruneExcessRows(long userId) {
        Long total = browseHistoryMapper.selectCount(
                Wrappers.<BrowseHistory>lambdaQuery().eq(BrowseHistory::getUserId, userId));
        int over = (int) (total - MAX_BROWSE_HISTORY);
        if (over <= 0) {
            return;
        }
        List<BrowseHistory> victims = browseHistoryMapper.selectList(
                Wrappers.<BrowseHistory>lambdaQuery()
                        .select(BrowseHistory::getId)
                        .eq(BrowseHistory::getUserId, userId)
                        .orderByAsc(BrowseHistory::getViewedAt)
                        .orderByAsc(BrowseHistory::getId)
                        .last("LIMIT " + over));
        if (victims.isEmpty()) {
            return;
        }
        List<Long> ids = victims.stream().map(BrowseHistory::getId).collect(Collectors.toList());
        browseHistoryMapper.delete(Wrappers.<BrowseHistory>lambdaQuery()
                .eq(BrowseHistory::getUserId, userId)
                .in(BrowseHistory::getId, ids));
    }

    public List<Recipe> recent(long userId, int limit) {
        int cap = Math.min(Math.max(limit, 1), 100);
        List<BrowseHistory> list = browseHistoryMapper.selectList(
                Wrappers.<BrowseHistory>lambdaQuery()
                        .eq(BrowseHistory::getUserId, userId)
                        .orderByDesc(BrowseHistory::getViewedAt)
                        .last("LIMIT 200"));
        List<Long> ids = list.stream().map(BrowseHistory::getRecipeId).distinct().collect(Collectors.toList());
        Map<Long, Recipe> byId = ids.isEmpty()
                ? Map.of()
                : recipeMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Recipe::getId, Function.identity(), (a, b) -> a));
        Map<Long, Recipe> ordered = new LinkedHashMap<>();
        for (BrowseHistory h : list) {
            Recipe r = byId.get(h.getRecipeId());
            if (r != null) {
                ordered.putIfAbsent(r.getId(), r);
            }
            if (ordered.size() >= cap) {
                break;
            }
        }
        return new ArrayList<>(ordered.values());
    }

    /**
     * 浏览历史：每药膳一条（最近浏览时间），按时间倒序；带 historyId / viewedAt 毫秒时间戳，供前台「我的」页展示。
     */
    public List<Map<String, Object>> listHistoryRecords(long userId, int limit) {
        int cap = Math.min(Math.max(limit, 1), 100);
        List<BrowseHistory> rows = browseHistoryMapper.selectList(
                Wrappers.<BrowseHistory>lambdaQuery()
                        .eq(BrowseHistory::getUserId, userId)
                        .orderByDesc(BrowseHistory::getViewedAt)
                        .orderByDesc(BrowseHistory::getId)
                        .last("LIMIT " + cap));
        List<Long> ids = rows.stream().map(BrowseHistory::getRecipeId).distinct().collect(Collectors.toList());
        Map<Long, Recipe> byId = ids.isEmpty()
                ? Map.of()
                : recipeMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Recipe::getId, Function.identity(), (a, b) -> a));
        ZoneId z = ZoneId.systemDefault();
        List<Map<String, Object>> out = new ArrayList<>();
        for (BrowseHistory h : rows) {
            Recipe r = byId.get(h.getRecipeId());
            if (r == null) {
                continue;
            }
            long viewedMs = h.getViewedAt() == null ? 0L : h.getViewedAt().atZone(z).toInstant().toEpochMilli();
            Map<String, Object> m = new HashMap<>();
            m.put("historyId", String.valueOf(h.getId()));
            m.put("viewedAt", viewedMs);
            m.put("recipeId", String.valueOf(r.getId()));
            m.put("name", r.getName());
            m.put("coverUrl", r.getCoverUrl() == null ? "" : r.getCoverUrl());
            m.put("efficacySummary", r.getEfficacySummary() == null ? "" : r.getEfficacySummary());
            out.add(m);
        }
        return out;
    }

    public void deleteHistoryRow(long userId, long historyId) {
        browseHistoryMapper.delete(Wrappers.<BrowseHistory>lambdaQuery()
                .eq(BrowseHistory::getUserId, userId)
                .eq(BrowseHistory::getId, historyId));
    }

    public void clearHistory(long userId) {
        browseHistoryMapper.delete(Wrappers.<BrowseHistory>lambdaQuery()
                .eq(BrowseHistory::getUserId, userId));
    }
}

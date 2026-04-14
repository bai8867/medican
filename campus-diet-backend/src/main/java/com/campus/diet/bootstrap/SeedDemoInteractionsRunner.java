package com.campus.diet.bootstrap;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.diet.entity.BrowseHistory;
import com.campus.diet.entity.Feedback;
import com.campus.diet.entity.Recipe;
import com.campus.diet.entity.SysUser;
import com.campus.diet.entity.UserFavorite;
import com.campus.diet.mapper.BrowseHistoryMapper;
import com.campus.diet.mapper.FeedbackMapper;
import com.campus.diet.mapper.RecipeMapper;
import com.campus.diet.mapper.SysUserMapper;
import com.campus.diet.mapper.UserFavoriteMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 为 admin / demo / student 写入演示收藏与浏览历史：按「该用户是否已有收藏」判断，避免只给 demo 灌数据导致常用 admin 账号仍为空。
 * 反馈表仍仅在全表为空时插入两条示例。
 */
@Component
@Order(35)
public class SeedDemoInteractionsRunner implements ApplicationRunner {

    private final SysUserMapper sysUserMapper;
    private final RecipeMapper recipeMapper;
    private final UserFavoriteMapper userFavoriteMapper;
    private final BrowseHistoryMapper browseHistoryMapper;
    private final FeedbackMapper feedbackMapper;

    @Value("${campus.diet.seed-demo-interactions:true}")
    private boolean seedDemoInteractions;

    public SeedDemoInteractionsRunner(
            SysUserMapper sysUserMapper,
            RecipeMapper recipeMapper,
            UserFavoriteMapper userFavoriteMapper,
            BrowseHistoryMapper browseHistoryMapper,
            FeedbackMapper feedbackMapper) {
        this.sysUserMapper = sysUserMapper;
        this.recipeMapper = recipeMapper;
        this.userFavoriteMapper = userFavoriteMapper;
        this.browseHistoryMapper = browseHistoryMapper;
        this.feedbackMapper = feedbackMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!seedDemoInteractions) {
            return;
        }
        Long adminId = userId("admin");
        Long demoId = userId("demo");
        Long studentId = userId("student");
        List<Long> recipeIds = recipeMapper
                .selectList(Wrappers.<Recipe>lambdaQuery().orderByAsc(Recipe::getId).last("LIMIT 24"))
                .stream()
                .map(Recipe::getId)
                .collect(Collectors.toList());
        if (recipeIds.isEmpty()) {
            return;
        }
        List<Long> seedUserIds = Arrays.asList(adminId, demoId, studentId);
        for (Long uid : seedUserIds) {
            if (uid == null) {
                continue;
            }
            long userFavCount = userFavoriteMapper.selectCount(
                    Wrappers.<UserFavorite>lambdaQuery().eq(UserFavorite::getUserId, uid));
            if (userFavCount == 0) {
                addFavorite(uid, pick(recipeIds, 0));
                addFavorite(uid, pick(recipeIds, 1));
                addFavorite(uid, pick(recipeIds, 2));
                addFavorite(uid, pick(recipeIds, 7));
            }
            long userHistCount = browseHistoryMapper.selectCount(
                    Wrappers.<BrowseHistory>lambdaQuery().eq(BrowseHistory::getUserId, uid));
            if (userHistCount == 0) {
                addHistory(uid, pick(recipeIds, 0), LocalDateTime.now().minusDays(2));
                addHistory(uid, pick(recipeIds, 3), LocalDateTime.now().minusDays(1));
            }
        }
        long fbTotal = feedbackMapper.selectCount(Wrappers.<Feedback>lambdaQuery());
        if (fbTotal == 0) {
            Long feedbackUserId = demoId != null ? demoId : (adminId != null ? adminId : studentId);
            Feedback f1 = new Feedback();
            f1.setUserId(feedbackUserId);
            f1.setContent("推荐结果很实用，希望食堂能有标注体质的窗口。");
            f1.setSource("app");
            feedbackMapper.insert(f1);
            Feedback f2 = new Feedback();
            f2.setUserId(null);
            f2.setContent("页面加载很流畅，问卷引导清晰。");
            f2.setSource("web");
            feedbackMapper.insert(f2);
        }
    }

    private Long userId(String username) {
        SysUser u = sysUserMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username).last("LIMIT 1"));
        return u != null ? u.getId() : null;
    }

    private static long pick(List<Long> ids, int index) {
        return ids.get(Math.min(index, ids.size() - 1));
    }

    private void addFavorite(long userId, long recipeId) {
        Long c = userFavoriteMapper.selectCount(
                Wrappers.<UserFavorite>lambdaQuery()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getRecipeId, recipeId));
        if (c != null && c > 0) {
            return;
        }
        UserFavorite f = new UserFavorite();
        f.setUserId(userId);
        f.setRecipeId(recipeId);
        userFavoriteMapper.insert(f);
    }

    private void addHistory(long userId, long recipeId, LocalDateTime viewedAt) {
        BrowseHistory h = new BrowseHistory();
        h.setUserId(userId);
        h.setRecipeId(recipeId);
        h.setViewedAt(viewedAt);
        browseHistoryMapper.insert(h);
    }
}

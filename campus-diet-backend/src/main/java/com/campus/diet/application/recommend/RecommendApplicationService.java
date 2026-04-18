package com.campus.diet.application.recommend;

import com.campus.diet.common.PageResult;
import com.campus.diet.domain.recommend.RecommendFilterDomainService;
import com.campus.diet.domain.recommend.RecommendScoringDomainService;
import com.campus.diet.domain.recommend.RecommendScoringDomainService.ScoredRecipe;
import com.campus.diet.entity.Recipe;
import com.campus.diet.infrastructure.recommend.RecommendQueryRepository;
import com.campus.diet.service.RuntimeMetricService;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class RecommendApplicationService {

    private final RecommendQueryRepository recommendQueryRepository;
    private final RecommendFilterDomainService filterDomainService;
    private final RecommendScoringDomainService scoringDomainService;
    private final RuntimeMetricService runtimeMetricService;
    private final int candidateWindowSize;
    private final String algorithmPath;

    public RecommendApplicationService(
            RecommendQueryRepository recommendQueryRepository,
            RecommendFilterDomainService filterDomainService,
            RecommendScoringDomainService scoringDomainService,
            RuntimeMetricService runtimeMetricService,
            int candidateWindowSize,
            String algorithmPath) {
        this.recommendQueryRepository = recommendQueryRepository;
        this.filterDomainService = filterDomainService;
        this.scoringDomainService = scoringDomainService;
        this.runtimeMetricService = runtimeMetricService;
        this.candidateWindowSize = candidateWindowSize;
        this.algorithmPath = algorithmPath == null ? "rules" : algorithmPath.trim().toLowerCase(Locale.ROOT);
    }

    public PageResult<Recipe> pageSceneRecipes(long sceneId, int page, int pageSize, String sortBy) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, pageSize);
        int total = recommendQueryRepository.countSceneRecipes(sceneId);
        int offset = Math.max(0, (safePage - 1) * safePageSize);
        List<Recipe> slice;
        if ("collect".equalsIgnoreCase(sortBy)) {
            slice = recommendQueryRepository.pageSceneByCollect(sceneId, offset, safePageSize);
        } else {
            List<Recipe> all = recommendQueryRepository.listByScene(sceneId);
            slice = offset >= all.size() ? List.of() : all.subList(offset, Math.min(all.size(), offset + safePageSize));
        }
        boolean hasMore = offset + slice.size() < total;
        return new PageResult<>(slice, total, safePage, safePageSize, hasMore);
    }

    public PageResult<ScoredRecipe> scoredRecommendFeedPage(
            int page,
            int pageSize,
            String sceneTag,
            String constitutionCode,
            boolean personalized,
            String seasonCode,
            String keyword,
            boolean globalEnabled) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, pageSize);
        int offset = Math.max(0, (safePage - 1) * safePageSize);
        long started = System.currentTimeMillis();
        runtimeMetricService.increment("recommend.request.total");
        if (!globalEnabled) {
            runtimeMetricService.increment("recommend.request.disabled");
            return new PageResult<>(List.of(), 0, safePage, safePageSize, false);
        }

        int candidateLimit = Math.min(800, Math.max(candidateWindowSize, (offset + safePageSize) * 4));
        List<Recipe> all = recommendQueryRepository.listRecommendCandidates(keyword, sceneTag, candidateLimit);
        all = filterDomainService.filterBySceneTag(all, sceneTag);
        all = filterDomainService.filterByKeyword(all, keyword);
        if (all.isEmpty()) {
            runtimeMetricService.increment("recommend.request.empty");
            return new PageResult<>(List.of(), 0, safePage, safePageSize, false);
        }

        String season = filterDomainService.resolveSeason(seasonCode);
        boolean useHybrid = personalized && "hybrid".equals(algorithmPath);
        RecommendQueryRepository.BehaviorProfile behaviorProfile =
                useHybrid ? recommendQueryRepository.loadBehaviorProfileForCurrentUser() : RecommendQueryRepository.BehaviorProfile.EMPTY;
        List<ScoredRecipe> scored =
                scoringDomainService.scoreAndSort(all, season, constitutionCode, personalized, useHybrid, filterDomainService, behaviorProfile);

        List<ScoredRecipe> slice = scored.stream()
                .skip(offset)
                .limit(safePageSize)
                .collect(Collectors.toList());
        boolean hasMore = offset + slice.size() < scored.size();
        runtimeMetricService.recordCostMs("recommend.request", System.currentTimeMillis() - started);
        if (slice.isEmpty()) {
            runtimeMetricService.increment("recommend.feed.page_empty");
        } else {
            runtimeMetricService.increment("recommend.feed.page_nonempty");
        }
        return new PageResult<>(slice, scored.size(), safePage, safePageSize, hasMore);
    }
}

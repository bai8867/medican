package com.campus.diet.domain.recommend;

import com.campus.diet.entity.Recipe;
import com.campus.diet.infrastructure.recommend.RecommendQueryRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class RecommendScoringDomainService {

    private final double matchWeight;
    private final double popularityWeight;
    private final double behaviorWeight;

    public RecommendScoringDomainService(double matchWeight, double popularityWeight, double behaviorWeight) {
        this.matchWeight = matchWeight;
        this.popularityWeight = popularityWeight;
        this.behaviorWeight = behaviorWeight;
    }

    public List<ScoredRecipe> scoreAndSort(
            List<Recipe> recipes,
            String seasonCode,
            String constitutionCode,
            boolean personalized,
            boolean useHybrid,
            RecommendFilterDomainService filterDomainService,
            RecommendQueryRepository.BehaviorProfile behaviorProfile) {
        if (recipes.isEmpty()) {
            return List.of();
        }
        double maxLog = recipes.stream().mapToDouble(r -> Math.log1p(safeCollectCount(r))).max().orElse(1);
        String userConstitution = constitutionCode == null ? "" : constitutionCode.trim();

        List<ScoredRecipe> scored = recipes.stream().map(r -> {
            double pop = Math.log1p(safeCollectCount(r)) / maxLog;
            double seasonFit = filterDomainService.seasonFit(r.getSeasonTags(), seasonCode);
            double constFit = filterDomainService.constitutionFit(r.getConstitutionTags(), userConstitution);
            double match = (personalized && !userConstitution.isEmpty())
                    ? (0.7 * constFit + 0.3 * seasonFit)
                    : seasonFit;
            double baseScore = matchWeight * match + popularityWeight * pop;
            double behaviorBoost = behaviorProfile.boostForRecipe(r);
            double totalScore = baseScore + (useHybrid ? behaviorWeight * behaviorBoost : 0.0);
            String reason = buildRecommendReason(match, pop, behaviorBoost, personalized, useHybrid);
            return new ScoredRecipe(r, totalScore, reason);
        }).collect(Collectors.toList());

        Comparator<ScoredRecipe> byScore = Comparator.comparingDouble((ScoredRecipe sr) -> sr.score).reversed();
        Comparator<ScoredRecipe> byNewerId = Comparator.comparingLong(
                (ScoredRecipe sr) -> sr.recipe.getId() == null ? 0L : sr.recipe.getId()).reversed();
        scored.sort(byScore.thenComparing(byNewerId));
        return scored;
    }

    private static int safeCollectCount(Recipe recipe) {
        return recipe.getCollectCount() == null ? 0 : recipe.getCollectCount();
    }

    private String buildRecommendReason(
            double matchScore,
            double popularityScore,
            double behaviorBoost,
            boolean personalized,
            boolean useHybrid) {
        StringBuilder sb = new StringBuilder();
        if (personalized) {
            sb.append(matchScore >= 0.75 ? "体质与时令匹配度高；" : "体质与时令整体匹配；");
        } else {
            sb.append("当前按时令与热度排序；");
        }
        if (popularityScore >= 0.7) {
            sb.append("收藏热度高。");
        } else if (popularityScore >= 0.4) {
            sb.append("近期热度稳定。");
        } else {
            sb.append("适合作为补充调养。");
        }
        if (useHybrid && behaviorBoost > 0.01) {
            sb.append("结合你的功效/体质等兴趣标签偏好加权。");
        }
        return sb.toString();
    }

    public static final class ScoredRecipe {
        private final Recipe recipe;
        private final double score;
        private final String recommendReason;

        public ScoredRecipe(Recipe recipe, double score, String recommendReason) {
            this.recipe = recipe;
            this.score = score;
            this.recommendReason = recommendReason;
        }

        public Recipe getRecipe() {
            return recipe;
        }

        public double getScore() {
            return score;
        }

        public String getRecommendReason() {
            return recommendReason;
        }
    }
}

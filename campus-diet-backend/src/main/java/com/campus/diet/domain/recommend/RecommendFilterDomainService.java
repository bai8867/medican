package com.campus.diet.domain.recommend;

import com.campus.diet.entity.Recipe;
import com.campus.diet.service.SeasonUtil;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class RecommendFilterDomainService {

    public List<Recipe> filterBySceneTag(List<Recipe> recipes, String sceneTag) {
        if (sceneTag == null || sceneTag.isBlank()) {
            return recipes;
        }
        String tag = sceneTag.trim();
        return recipes.stream()
                .filter(r -> containsTag(r.getEfficacyTags(), tag) || containsTag(r.getConstitutionTags(), tag))
                .collect(Collectors.toList());
    }

    public List<Recipe> filterByKeyword(List<Recipe> recipes, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return recipes;
        }
        String needle = keyword.trim();
        return recipes.stream().filter(r -> recipeMatchesKeyword(r, needle)).collect(Collectors.toList());
    }

    public String resolveSeason(String seasonCode) {
        return seasonCode != null && !seasonCode.isBlank()
                ? seasonCode
                : SeasonUtil.currentSeasonCode(LocalDate.now());
    }

    public double seasonFit(String seasonTags, String season) {
        if (seasonTags == null || seasonTags.isBlank()) {
            return 0.35;
        }
        boolean hit = Arrays.stream(seasonTags.split(","))
                .map(String::trim)
                .anyMatch(s -> s.equalsIgnoreCase(season));
        return hit ? 1.0 : 0.25;
    }

    public double constitutionFit(String constitutionTags, String userCode) {
        if (userCode == null || userCode.isEmpty()) {
            return 0.5;
        }
        if (constitutionTags == null || constitutionTags.isBlank()) {
            return 0.2;
        }
        boolean hit = Arrays.stream(constitutionTags.split(","))
                .map(String::trim)
                .anyMatch(s -> s.equalsIgnoreCase(userCode));
        return hit ? 1.0 : 0.15;
    }

    private boolean containsTag(String csv, String needle) {
        if (csv == null || csv.isBlank()) {
            return false;
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .anyMatch(t -> t.equalsIgnoreCase(needle));
    }

    private boolean recipeMatchesKeyword(Recipe recipe, String keyword) {
        if (textContains(recipe.getName(), keyword)) {
            return true;
        }
        if (textContains(recipe.getEfficacySummary(), keyword)) {
            return true;
        }
        if (textContains(recipe.getInstructionSummary(), keyword)) {
            return true;
        }
        if (csvContainsSubstring(recipe.getEfficacyTags(), keyword)) {
            return true;
        }
        if (csvContainsSubstring(recipe.getConstitutionTags(), keyword)) {
            return true;
        }
        return csvContainsSubstring(recipe.getSymptomTags(), keyword);
    }

    private static boolean textContains(String haystack, String needle) {
        if (haystack == null || haystack.isBlank()) {
            return false;
        }
        if (haystack.contains(needle)) {
            return true;
        }
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static boolean csvContainsSubstring(String csv, String needle) {
        if (csv == null || csv.isBlank()) {
            return false;
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .anyMatch(t -> textContains(t, needle));
    }
}

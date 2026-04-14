package com.campus.diet.dto;

import com.campus.diet.entity.Recipe;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 与前端 {@code Recipe} 摘要字段对齐（id 为字符串）。
 * <p>推荐页会在前端按功效/体质/季节再筛选，须带上标签字段，否则列表会被滤空。</p>
 */
@Data
@NoArgsConstructor
public class RecipeCardDto {

    private static final Map<String, String> CONSTITUTION_CODE_TO_LABEL;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("pinghe", "平和质");
        m.put("qixu", "气虚质");
        m.put("yangxu", "阳虚质");
        m.put("yinxu", "阴虚质");
        m.put("tanshi", "痰湿质");
        m.put("shire", "湿热质");
        m.put("xueyu", "血瘀质");
        m.put("qiyu", "气郁质");
        m.put("tebing", "特禀质");
        CONSTITUTION_CODE_TO_LABEL = Collections.unmodifiableMap(m);
    }

    private String id;
    private String name;
    private String coverUrl;
    private int collectCount;
    private String efficacySummary;
    /** 与前端 filterByEffect 的 substring 分支对齐（功效摘要全文） */
    private String effect;
    /** 功效标签，对应库表 efficacy_tags */
    private List<String> effectTags;
    /** 适宜季节编码：spring / summer / autumn / winter，对应 season_tags */
    private List<String> seasonFit;
    /** 适宜体质中文名，由 constitution_tags（code CSV）映射 */
    private List<String> suitConstitutions;
    /** 场景方案页：为何适合当前场景 */
    private String whyFit;
    /** 与场景痛点命中的关键词 */
    private List<String> matchedPainTags;

    public static RecipeCardDto from(Recipe r) {
        RecipeCardDto d = new RecipeCardDto();
        d.setId(String.valueOf(r.getId()));
        d.setName(r.getName());
        d.setCoverUrl(r.getCoverUrl());
        d.setCollectCount(r.getCollectCount() == null ? 0 : r.getCollectCount());
        d.setEfficacySummary(r.getEfficacySummary());
        d.setEffect(r.getEfficacySummary());
        d.setEffectTags(splitCsv(r.getEfficacyTags()));
        List<String> seasons = splitCsv(r.getSeasonTags()).stream()
                .map(String::toLowerCase)
                .collect(Collectors.toList());
        d.setSeasonFit(seasons.isEmpty() ? List.of("all") : seasons);
        d.setSuitConstitutions(mapConstitutionLabels(r.getConstitutionTags()));
        return d;
    }

    private static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private static List<String> mapConstitutionLabels(String csv) {
        List<String> raw = splitCsv(csv);
        if (raw.isEmpty()) {
            return List.of();
        }
        List<String> labels = new ArrayList<>(raw.size());
        for (String token : raw) {
            String key = token.toLowerCase();
            labels.add(CONSTITUTION_CODE_TO_LABEL.getOrDefault(key, token));
        }
        return labels;
    }
}

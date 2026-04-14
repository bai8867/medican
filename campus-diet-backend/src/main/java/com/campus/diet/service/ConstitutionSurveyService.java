package com.campus.diet.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 简化版九种体质问卷：9 题，每题 1–5 分，对应 9 个维度各一题；主类型取最高分，
 * 若最高分与平和质差距小于阈值且平和质分数不低，则倾向判定为平和质（教学演示用）。
 */
@Service
public class ConstitutionSurveyService {

    private static final String[] CODES = {
            "pinghe", "qixu", "yangxu", "yinxu", "tanshi", "shire", "xueyu", "qiyu", "tebing"
    };
    private static final String[] LABELS = {
            "平和质", "气虚质", "阳虚质", "阴虚质", "痰湿质", "湿热质", "血瘀质", "气郁质", "特禀质"
    };

    private final ObjectMapper objectMapper = new ObjectMapper();

    public SurveyResult evaluate(List<Integer> answers) {
        if (answers == null || answers.size() != 9) {
            throw new IllegalArgumentException("问卷需提交 9 个整型得分（1-5）");
        }
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (int i = 0; i < 9; i++) {
            int v = answers.get(i);
            if (v < 1 || v > 5) {
                throw new IllegalArgumentException("每题得分须在 1-5 之间");
            }
            scores.put(CODES[i], v);
        }
        String primary = resolvePrimary(scores);
        String label = labelFor(primary);
        try {
            return new SurveyResult(primary, label, scores, objectMapper.writeValueAsString(scores));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String resolvePrimary(Map<String, Integer> scores) {
        int max = scores.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<String> tops = new ArrayList<>();
        for (Map.Entry<String, Integer> e : scores.entrySet()) {
            if (e.getValue() == max) {
                tops.add(e.getKey());
            }
        }
        int pinghe = scores.getOrDefault("pinghe", 0);
        if (tops.contains("pinghe")) {
            return "pinghe";
        }
        // 平和质缓冲：最高分不高且与平和质接近时给平和质（演示规则）
        if (pinghe >= 3 && max - pinghe <= 1 && max <= 4) {
            return "pinghe";
        }
        return tops.get(0);
    }

    private String labelFor(String code) {
        for (int i = 0; i < CODES.length; i++) {
            if (CODES[i].equals(code)) {
                return LABELS[i];
            }
        }
        return "";
    }

    public static class SurveyResult {
        public final String primaryCode;
        public final String primaryLabel;
        public final Map<String, Integer> scores;
        public final String scoresJson;

        public SurveyResult(String primaryCode, String primaryLabel, Map<String, Integer> scores, String scoresJson) {
            this.primaryCode = primaryCode;
            this.primaryLabel = primaryLabel;
            this.scores = scores;
            this.scoresJson = scoresJson;
        }
    }
}

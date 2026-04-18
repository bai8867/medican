package com.campus.diet.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConstitutionSurveyServiceTest {

    private final ConstitutionSurveyService service = new ConstitutionSurveyService();

    @Test
    void evaluate_shouldSupportResearchVersionAndReturnSecondaryCodes() {
        List<Integer> answers = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            answers.add(2);
        }
        // qixu (6-10) 强，qiyu (36-40) 次强
        for (int i = 5; i < 10; i++) {
            answers.set(i, 5);
        }
        for (int i = 35; i < 40; i++) {
            answers.set(i, 4);
        }

        ConstitutionSurveyService.SurveyResult result = service.evaluate(answers, "v2-research-hybrid");

        assertEquals("qixu", result.primaryCode);
        assertNotNull(result.secondaryCodes);
        assertTrue(result.secondaryCodes.contains("qiyu"));
        assertTrue(result.confidence >= 0 && result.confidence <= 1);
        assertNotNull(result.ruleTrace);
        assertFalse(result.ruleTrace.isEmpty());
    }

    @Test
    void evaluate_shouldKeepLegacyNineAsBaselineComparator() {
        List<Integer> answers = List.of(4, 3, 3, 3, 3, 3, 3, 3, 4);
        ConstitutionSurveyService.SurveyResult result = service.evaluateLegacyNine(answers);
        assertEquals("qixu", result.primaryCode);
    }
}

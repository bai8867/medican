package com.campus.diet.service.ai;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiTherapyPlanDisabledResponseFactoryTest {

    @Test
    void build_containsDisabledMarkers() {
        AiTherapyPlanDisabledResponseFactory factory = new AiTherapyPlanDisabledResponseFactory();
        Map<String, Object> m = factory.build("咽干口渴", "阴虚质");
        assertFalse((Boolean) m.get("enabled"));
        assertEquals("咽干口渴", m.get("symptomSummary"));
        assertEquals("阴虚质", m.get("constitutionApplied"));
        assertTrue(String.valueOf(m.get("therapyRecommendMarkdown")).contains("功能已由管理员关闭"));
    }

    @Test
    void build_emptySymptom_usesDailyCareLabel() {
        AiTherapyPlanDisabledResponseFactory factory = new AiTherapyPlanDisabledResponseFactory();
        Map<String, Object> m = factory.build("", "平和质");
        assertEquals("日常调养", m.get("symptomSummary"));
    }
}

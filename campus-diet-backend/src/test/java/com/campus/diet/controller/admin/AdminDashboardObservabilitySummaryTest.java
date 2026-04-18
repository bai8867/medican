package com.campus.diet.controller.admin;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AdminDashboardObservabilitySummaryTest {

    @Test
    void buildObservabilitySummary_computesHttpErrorRateAndRatios() {
        Map<String, Long> counters = new LinkedHashMap<>();
        counters.put("http.request.total", 100L);
        counters.put("http.request.error.none", 94L);
        counters.put("http.request.error.server", 2L);
        counters.put("http.request.error.client", 3L);
        counters.put("http.request.error.auth", 1L);
        counters.put("http.request.error.validation", 0L);
        counters.put("recommend.feed.page_nonempty", 40L);
        counters.put("recommend.feed.page_empty", 10L);
        counters.put("ai.generate.upstream.success", 8L);
        counters.put("ai.generate.upstream.failed", 2L);
        counters.put("ai.diet.upstream.success", 5L);
        counters.put("ai.diet.upstream.failed", 1L);
        counters.put("ai.generate.therapy.prompt_budget.observed", 4L);
        counters.put("ai.generate.therapy.prompt_budget.chars_total", 8000L);
        counters.put("ai.diet.prompt_budget.observed", 2L);
        counters.put("ai.diet.prompt_budget.chars_total", 3000L);
        Map<String, Long> avg = new LinkedHashMap<>();
        avg.put("http.request.cost", 12L);
        Map<String, Long> max = new LinkedHashMap<>();
        max.put("http.request.cost", 200L);
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("counters", counters);
        snap.put("avgCostMs", avg);
        snap.put("maxCostMs", max);

        Map<String, Object> s = AdminDashboardController.buildObservabilitySummary(snap);

        assertEquals(0.06, (Double) s.get("http.error_rate_classified"), 1e-9);
        assertEquals(0.8, (Double) s.get("recommend.feed.nonempty_ratio"), 1e-9);
        assertEquals(0.8, (Double) s.get("ai.therapy.upstream_ok_ratio"), 1e-9);
        assertEquals(5.0 / 6.0, (Double) s.get("ai.diet.upstream_ok_ratio"), 1e-9);
        assertEquals(12L, s.get("http.avg_cost_ms"));
        assertEquals(200L, s.get("http.max_cost_ms"));
        assertEquals(2000.0, (Double) s.get("ai.therapy.prompt_budget.avg_utf16_content_units"), 1e-9);
        assertEquals(1500.0, (Double) s.get("ai.diet.prompt_budget.avg_utf16_content_units"), 1e-9);
        assertEquals(8000L, s.get("ai.therapy.prompt_budget.chars_total"));
        assertEquals(3000L, s.get("ai.diet.prompt_budget.chars_total"));
    }

    @Test
    void buildObservabilitySummary_nullRatiosWhenDenominatorZero() {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("counters", Map.of("http.request.total", 0L));
        snap.put("avgCostMs", Map.of());
        snap.put("maxCostMs", Map.of());
        Map<String, Object> s = AdminDashboardController.buildObservabilitySummary(snap);
        assertNull(s.get("http.error_rate_classified"));
        assertNull(s.get("recommend.feed.nonempty_ratio"));
        assertNotNull(s.get("http.request.total"));
        assertNull(s.get("ai.therapy.prompt_budget.avg_utf16_content_units"));
        assertNull(s.get("ai.diet.prompt_budget.avg_utf16_content_units"));
    }
}

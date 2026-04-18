package com.campus.diet.service.ai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmPromptBudgetHooksTest {

    @Test
    void sumMessageUtf16Units_sumsContentOnly() {
        int n =
                LlmPromptBudgetHooks.sumMessageUtf16Units(
                        List.of(
                                Map.of("role", "system", "content", "ab"),
                                Map.of("role", "user", "content", "cde")));
        assertEquals(5, n);
    }

    @Test
    void approxTokensHeuristic_roundsUpQuarter() {
        assertEquals(0, LlmPromptBudgetHooks.approxTokensHeuristic(0));
        assertEquals(1, LlmPromptBudgetHooks.approxTokensHeuristic(1));
        assertEquals(1, LlmPromptBudgetHooks.approxTokensHeuristic(4));
        assertEquals(2, LlmPromptBudgetHooks.approxTokensHeuristic(5));
    }
}

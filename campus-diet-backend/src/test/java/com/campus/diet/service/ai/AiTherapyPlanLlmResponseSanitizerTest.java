package com.campus.diet.service.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiTherapyPlanLlmResponseSanitizerTest {

    @Test
    void stripMarkdownFence_plainJsonUnchanged() {
        String raw = "{\"a\":1}";
        assertEquals(raw, AiTherapyPlanLlmResponseSanitizer.stripMarkdownFence(raw));
    }

    @Test
    void stripMarkdownFence_removesFence() {
        String raw = "```json\n{\"x\":2}\n```";
        assertEquals("{\"x\":2}", AiTherapyPlanLlmResponseSanitizer.stripMarkdownFence(raw));
    }

    @Test
    void stripMarkdownFence_nullToEmpty() {
        assertEquals("", AiTherapyPlanLlmResponseSanitizer.stripMarkdownFence(null));
    }
}

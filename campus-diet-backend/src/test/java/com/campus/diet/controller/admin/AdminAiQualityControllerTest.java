package com.campus.diet.controller.admin;

import com.campus.diet.common.BizException;
import com.campus.diet.entity.AiIssueSample;
import com.campus.diet.security.LoginUser;
import com.campus.diet.security.LoginUserHolder;
import com.campus.diet.security.Roles;
import com.campus.diet.service.AiIssueSampleService;
import com.campus.diet.service.SystemKvService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminAiQualityControllerTest {

    private final SystemKvService systemKvService = mock(SystemKvService.class);
    private final AiIssueSampleService aiIssueSampleService = mock(AiIssueSampleService.class);
    private final AdminAiQualityController controller = new AdminAiQualityController(systemKvService, aiIssueSampleService);

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    @Test
    void rules_shouldRejectWhenNotAdmin() {
        LoginUserHolder.set(new LoginUser(12L, "canteen", Roles.CANTEEN_MANAGER));
        BizException ex = assertThrows(BizException.class, controller::rules);
        assertEquals(403, ex.getCode());
    }

    @Test
    void rules_shouldReturnConfigForAdmin() {
        LoginUserHolder.set(new LoginUser(1L, "admin", Roles.ADMIN));
        when(systemKvService.flagOn("ai.quality.guard.enabled", true)).thenReturn(true);
        when(systemKvService.flagOn("ai.quality.safety.strict", true)).thenReturn(false);
        when(systemKvService.get("ai.quality.score.threshold", "75")).thenReturn("80");

        Map<String, Object> data = controller.rules().getData();

        assertEquals(true, data.get("guardEnabled"));
        assertEquals(false, data.get("strictSafety"));
        assertEquals(80, data.get("scoreThreshold"));
    }

    @Test
    void updateRules_shouldPersistAndReturnLatest() {
        LoginUserHolder.set(new LoginUser(1L, "admin", Roles.ADMIN));
        when(systemKvService.flagOn("ai.quality.guard.enabled", true)).thenReturn(false);
        when(systemKvService.flagOn("ai.quality.safety.strict", true)).thenReturn(true);
        when(systemKvService.get("ai.quality.score.threshold", "75")).thenReturn("70");

        AdminAiQualityController.UpdateRulesBody body = new AdminAiQualityController.UpdateRulesBody();
        body.setGuardEnabled(false);
        body.setStrictSafety(true);
        body.setScoreThreshold(70);

        Map<String, Object> data = controller.updateRules(body).getData();

        assertNotNull(data);
    }

    @Test
    void samples_shouldReturnPagedList() {
        LoginUserHolder.set(new LoginUser(1L, "admin", Roles.ADMIN));
        AiIssueSample sample = new AiIssueSample();
        sample.setId(10L);
        sample.setSymptom("咳嗽");
        when(aiIssueSampleService.listLatest(1, 20, true)).thenReturn(List.of(sample));
        when(aiIssueSampleService.count(anyBoolean())).thenReturn(1L);

        Map<String, Object> data = controller.samples(1, 20, true).getData();

        assertEquals(1L, data.get("total"));
    }

    @Test
    void replay_shouldReturnResult() {
        LoginUserHolder.set(new LoginUser(1L, "admin", Roles.ADMIN));
        when(aiIssueSampleService.replay(10L)).thenReturn(Map.of("ok", true, "score", 76));

        Map<String, Object> data = controller.replay(10L).getData();

        assertEquals(true, data.get("ok"));
    }
}

package com.campus.diet.controller;

import com.campus.diet.common.BizException;
import com.campus.diet.security.LoginUser;
import com.campus.diet.security.LoginUserHolder;
import com.campus.diet.security.Roles;
import com.campus.diet.service.BrowseHistoryService;
import com.campus.diet.service.FavoriteService;
import com.campus.diet.service.RuntimeMetricService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class FavoriteHistoryControllerTest {

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    @Test
    void removeFavorite_shouldRejectNonPositiveRecipeId() {
        LoginUserHolder.set(new LoginUser(1L, "admin", Roles.ADMIN));
        FavoriteHistoryController controller = new FavoriteHistoryController(
                mock(FavoriteService.class),
                mock(BrowseHistoryService.class),
                new RuntimeMetricService());

        BizException exception = assertThrows(BizException.class, () -> controller.removeFavorite("0"));

        assertEquals(400, exception.getCode());
    }
}

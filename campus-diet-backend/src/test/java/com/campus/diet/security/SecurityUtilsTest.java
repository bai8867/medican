package com.campus.diet.security;

import com.campus.diet.common.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityUtilsTest {

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    @Test
    void requireLogin_shouldThrow401WhenNoUser() {
        BizException exception = assertThrows(BizException.class, SecurityUtils::requireLogin);
        assertEquals(401, exception.getCode());
    }

    @Test
    void requireContentManager_shouldThrow403ForUserRole() {
        LoginUserHolder.set(new LoginUser(100L, "u", Roles.USER));

        BizException exception = assertThrows(BizException.class, SecurityUtils::requireContentManager);

        assertEquals(403, exception.getCode());
    }

    @Test
    void requireContentManager_shouldAllowCanteenManager() {
        LoginUserHolder.set(new LoginUser(100L, "u", Roles.CANTEEN_MANAGER));
        SecurityUtils.requireContentManager();
    }

    @Test
    void requireAdmin_shouldAllowOnlyAdmin() {
        LoginUserHolder.set(new LoginUser(100L, "u", Roles.CANTEEN_MANAGER));
        BizException forbidden = assertThrows(BizException.class, SecurityUtils::requireAdmin);
        assertEquals(403, forbidden.getCode());

        LoginUserHolder.set(new LoginUser(101L, "admin", Roles.ADMIN));
        SecurityUtils.requireAdmin();
    }
}

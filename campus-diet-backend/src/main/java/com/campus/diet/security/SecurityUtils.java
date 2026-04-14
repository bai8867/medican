package com.campus.diet.security;

import com.campus.diet.common.BizException;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static LoginUser requireLogin() {
        LoginUser u = LoginUserHolder.get();
        if (u == null) {
            throw new BizException(401, "请先登录");
        }
        return u;
    }

    public static void requireContentManager() {
        LoginUser u = requireLogin();
        if (!Roles.canManageContent(u.getRole())) {
            throw new BizException(403, "需要管理员或食堂负责人权限");
        }
    }

    public static void requireAdmin() {
        LoginUser u = requireLogin();
        if (!Roles.isAdmin(u.getRole())) {
            throw new BizException(403, "需要管理员权限");
        }
    }
}

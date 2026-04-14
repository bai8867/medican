package com.campus.diet.security;

public final class Roles {

    public static final String USER = "USER";
    public static final String ADMIN = "ADMIN";
    public static final String CANTEEN_MANAGER = "CANTEEN_MANAGER";

    private Roles() {
    }

    public static boolean isAdmin(String role) {
        return ADMIN.equalsIgnoreCase(role);
    }

    public static boolean isCanteen(String role) {
        return CANTEEN_MANAGER.equalsIgnoreCase(role);
    }

    public static boolean canManageContent(String role) {
        return isAdmin(role) || isCanteen(role);
    }
}

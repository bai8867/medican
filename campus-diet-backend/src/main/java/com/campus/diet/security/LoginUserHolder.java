package com.campus.diet.security;

public final class LoginUserHolder {

    private static final ThreadLocal<LoginUser> TL = new ThreadLocal<>();

    private LoginUserHolder() {
    }

    public static void set(LoginUser user) {
        TL.set(user);
    }

    public static LoginUser get() {
        return TL.get();
    }

    public static void clear() {
        TL.remove();
    }
}

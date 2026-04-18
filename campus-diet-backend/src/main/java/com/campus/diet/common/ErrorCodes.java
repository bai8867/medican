package com.campus.diet.common;

/** 与前端约定的业务错误码（HTTP 体 {@link ApiResponse#getCode()}，可与 HTTP 状态码并存）。 */
public final class ErrorCodes {

    private ErrorCodes() {}

    /** 账号被管理员禁用：携带旧 JWT 的请求须拒绝，登录时亦返回此码。 */
    public static final int ACCOUNT_DISABLED = 4031;
}

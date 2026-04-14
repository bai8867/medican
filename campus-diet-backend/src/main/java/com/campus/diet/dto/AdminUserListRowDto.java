package com.campus.diet.dto;

import lombok.Data;

/**
 * 管理端用户列表行，与前台 {@code UserManage.vue} 字段对齐。
 */
@Data
public class AdminUserListRowDto {

    private String id;
    private String constitutionCode;
    private String constitutionLabel;
    /** ISO-8601 风格字符串，来自 {@code sys_user.created_at} */
    private String registeredAt;
    /** {@code active} 或 {@code disabled} */
    private String status;
}

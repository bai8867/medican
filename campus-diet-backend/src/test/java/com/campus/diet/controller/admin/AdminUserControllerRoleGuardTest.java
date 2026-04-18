package com.campus.diet.controller.admin;

import com.campus.diet.common.BizException;
import com.campus.diet.entity.SysUser;
import com.campus.diet.mapper.RecipeMapper;
import com.campus.diet.mapper.SysUserMapper;
import com.campus.diet.mapper.UserFavoriteMapper;
import com.campus.diet.mapper.UserProfileMapper;
import com.campus.diet.security.LoginUser;
import com.campus.diet.security.LoginUserHolder;
import com.campus.diet.security.Roles;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserControllerRoleGuardTest {

    private final SysUserMapper sysUserMapper = mock(SysUserMapper.class);
    private final UserProfileMapper userProfileMapper = mock(UserProfileMapper.class);
    private final UserFavoriteMapper userFavoriteMapper = mock(UserFavoriteMapper.class);
    private final RecipeMapper recipeMapper = mock(RecipeMapper.class);
    private final AdminUserController controller =
            new AdminUserController(
                    sysUserMapper,
                    userProfileMapper,
                    userFavoriteMapper,
                    recipeMapper,
                    new ObjectMapper());

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    @Test
    void update_shouldRejectEscalatingStudentToAdmin() {
        LoginUserHolder.set(new LoginUser(1L, "admin", Roles.ADMIN));
        SysUser student = new SysUser();
        student.setId(101L);
        student.setUsername("student");
        student.setRole(Roles.USER);
        student.setStatus(1);
        when(sysUserMapper.selectById(101L)).thenReturn(student);

        AdminUserController.UserPatch patch = new AdminUserController.UserPatch();
        patch.setRole(Roles.ADMIN);

        BizException e = assertThrows(BizException.class, () -> controller.update(101L, patch));
        assertEquals(400, e.getCode());
        verify(sysUserMapper, never()).updateById(student);
    }

    @Test
    void update_shouldAllowStudentRoleAndStatusChange() {
        LoginUserHolder.set(new LoginUser(1L, "admin", Roles.ADMIN));
        SysUser student = new SysUser();
        student.setId(102L);
        student.setUsername("student2");
        student.setRole(Roles.USER);
        student.setStatus(1);
        when(sysUserMapper.selectById(102L)).thenReturn(student);

        AdminUserController.UserPatch patch = new AdminUserController.UserPatch();
        patch.setRole(Roles.USER);
        patch.setStatus(0);

        controller.update(102L, patch);

        assertEquals(Roles.USER, student.getRole());
        assertEquals(0, student.getStatus());
        verify(sysUserMapper).updateById(student);
    }
}

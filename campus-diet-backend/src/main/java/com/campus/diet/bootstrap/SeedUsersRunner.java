package com.campus.diet.bootstrap;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.diet.entity.SysUser;
import com.campus.diet.entity.UserProfile;
import com.campus.diet.mapper.SysUserMapper;
import com.campus.diet.mapper.UserProfileMapper;
import com.campus.diet.security.Roles;
import com.campus.diet.service.SeasonUtil;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@Order(20)
public class SeedUsersRunner implements ApplicationRunner {

    private final SysUserMapper sysUserMapper;
    private final UserProfileMapper userProfileMapper;
    private final PasswordEncoder passwordEncoder;

    public SeedUsersRunner(
            SysUserMapper sysUserMapper,
            UserProfileMapper userProfileMapper,
            PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.userProfileMapper = userProfileMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureUser("admin", "admin123", Roles.ADMIN);
        ensureUser("canteen", "canteen123", Roles.CANTEEN_MANAGER);
        /** 与前台后台登录文案 canteen_manager 一致 */
        ensureUser("canteen_manager", "canteen123", Roles.CANTEEN_MANAGER);
        ensureUser("demo", "demo123", Roles.USER);
        ensureUser("student", "123456", Roles.USER);
    }

    private void ensureUser(String username, String rawPassword, String role) {
        Long c = sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
        if (c != null && c > 0) {
            return;
        }
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(rawPassword));
        u.setRole(role);
        u.setStatus(1);
        sysUserMapper.insert(u);
        UserProfile p = new UserProfile();
        p.setUserId(u.getId());
        p.setRecommendEnabled(1);
        p.setDataCollectionEnabled(1);
        p.setSeasonCode(SeasonUtil.currentSeasonCode(LocalDate.now()));
        userProfileMapper.insert(p);
    }
}

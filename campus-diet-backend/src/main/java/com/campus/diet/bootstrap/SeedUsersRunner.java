package com.campus.diet.bootstrap;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.diet.entity.SysUser;
import com.campus.diet.entity.UserProfile;
import com.campus.diet.mapper.SysUserMapper;
import com.campus.diet.mapper.UserProfileMapper;
import com.campus.diet.security.Roles;
import com.campus.diet.service.SeasonUtil;
import org.springframework.beans.factory.annotation.Value;
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
    private final String adminSeedPassword;
    private final String canteenSeedPassword;
    private final String demoSeedPassword;
    private final String studentSeedPassword;

    public SeedUsersRunner(
            SysUserMapper sysUserMapper,
            UserProfileMapper userProfileMapper,
            PasswordEncoder passwordEncoder,
            @Value("${campus.seed-users.admin-password:SeedAdmin#2026!}") String adminSeedPassword,
            @Value("${campus.seed-users.canteen-password:SeedCanteen#2026!}") String canteenSeedPassword,
            @Value("${campus.seed-users.demo-password:SeedDemo#2026!}") String demoSeedPassword,
            @Value("${campus.seed-users.student-password:SeedStudent#2026!}") String studentSeedPassword) {
        this.sysUserMapper = sysUserMapper;
        this.userProfileMapper = userProfileMapper;
        this.passwordEncoder = passwordEncoder;
        this.adminSeedPassword = adminSeedPassword;
        this.canteenSeedPassword = canteenSeedPassword;
        this.demoSeedPassword = demoSeedPassword;
        this.studentSeedPassword = studentSeedPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureUser("admin", adminSeedPassword, Roles.ADMIN);
        ensureUser("canteen", canteenSeedPassword, Roles.CANTEEN_MANAGER);
        /** 与前台后台登录文案 canteen_manager 一致 */
        ensureUser("canteen_manager", canteenSeedPassword, Roles.CANTEEN_MANAGER);
        ensureUser("demo", demoSeedPassword, Roles.USER);
        ensureUser("student", studentSeedPassword, Roles.USER);
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

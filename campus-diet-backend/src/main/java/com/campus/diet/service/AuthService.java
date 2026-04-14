package com.campus.diet.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.diet.common.BizException;
import com.campus.diet.common.ErrorCodes;
import com.campus.diet.entity.SysUser;
import com.campus.diet.entity.UserProfile;
import com.campus.diet.mapper.SysUserMapper;
import com.campus.diet.mapper.UserProfileMapper;
import com.campus.diet.security.JwtService;
import com.campus.diet.security.LoginUser;
import com.campus.diet.security.Roles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final UserProfileMapper userProfileMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            SysUserMapper sysUserMapper,
            UserProfileMapper userProfileMapper,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.sysUserMapper = sysUserMapper;
        this.userProfileMapper = userProfileMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginToken login(String username, String password) {
        SysUser u = sysUserMapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
        if (u == null) {
            throw new BizException(401, "用户名或密码错误");
        }
        if (u.getStatus() != null && u.getStatus() == 0) {
            throw new BizException(ErrorCodes.ACCOUNT_DISABLED, "账号已被禁用，请重新注册");
        }
        if (!passwordEncoder.matches(password, u.getPassword())) {
            throw new BizException(401, "用户名或密码错误");
        }
        String token = jwtService.createToken(u.getId(), u.getUsername(), u.getRole());
        return new LoginToken(token, u);
    }

    /**
     * 自助注册。仅允许 {@link Roles#USER} 与 {@link Roles#CANTEEN_MANAGER}，禁止自助创建管理员。
     *
     * @param roleParam 可为 null/空白，视为学生 {@link Roles#USER}
     */
    @Transactional
    public LoginToken register(String username, String password, String roleParam) {
        Long cnt = sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
        if (cnt != null && cnt > 0) {
            throw new BizException(400, "用户名已存在");
        }
        String role = resolveRegisterRole(roleParam);
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(password));
        u.setRole(role);
        u.setStatus(1);
        sysUserMapper.insert(u);
        if (Roles.USER.equals(role)) {
            UserProfile p = new UserProfile();
            p.setUserId(u.getId());
            p.setRecommendEnabled(1);
            p.setDataCollectionEnabled(1);
            p.setSeasonCode(SeasonUtil.currentSeasonCode(java.time.LocalDate.now()));
            userProfileMapper.insert(p);
        }
        String token = jwtService.createToken(u.getId(), u.getUsername(), u.getRole());
        return new LoginToken(token, u);
    }

    private static String resolveRegisterRole(String roleParam) {
        if (roleParam == null || roleParam.isBlank()) {
            return Roles.USER;
        }
        String r = roleParam.trim().toUpperCase();
        if (Roles.ADMIN.equals(r)) {
            throw new BizException(400, "不允许自助注册管理员");
        }
        if (Roles.USER.equals(r) || Roles.CANTEEN_MANAGER.equals(r)) {
            return r;
        }
        throw new BizException(400, "注册角色无效，请使用 USER 或 CANTEEN_MANAGER");
    }

    public static class LoginToken {
        public final String token;
        public final SysUser user;

        public LoginToken(String token, SysUser user) {
            this.token = token;
            this.user = user;
        }
    }
}

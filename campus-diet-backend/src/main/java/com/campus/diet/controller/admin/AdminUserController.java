package com.campus.diet.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.diet.common.ApiResponse;
import com.campus.diet.common.BizException;
import com.campus.diet.common.PageResult;
import com.campus.diet.dto.AdminUserListRowDto;
import com.campus.diet.entity.SysUser;
import com.campus.diet.entity.UserFavorite;
import com.campus.diet.entity.UserProfile;
import com.campus.diet.mapper.RecipeMapper;
import com.campus.diet.mapper.SysUserMapper;
import com.campus.diet.mapper.UserFavoriteMapper;
import com.campus.diet.mapper.UserProfileMapper;
import com.campus.diet.security.Roles;
import com.campus.diet.security.SecurityUtils;
import com.campus.diet.util.ConstitutionLabelUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final SysUserMapper sysUserMapper;
    private final UserProfileMapper userProfileMapper;
    private final UserFavoriteMapper userFavoriteMapper;
    private final RecipeMapper recipeMapper;
    private final ObjectMapper objectMapper;

    public AdminUserController(
            SysUserMapper sysUserMapper,
            UserProfileMapper userProfileMapper,
            UserFavoriteMapper userFavoriteMapper,
            RecipeMapper recipeMapper,
            ObjectMapper objectMapper) {
        this.sysUserMapper = sysUserMapper;
        this.userProfileMapper = userProfileMapper;
        this.userFavoriteMapper = userFavoriteMapper;
        this.recipeMapper = recipeMapper;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ApiResponse<PageResult<AdminUserListRowDto>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "10") int pageSize,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "constitutionCode", required = false) String constitutionCode) {
        SecurityUtils.requireAdmin();
        LambdaQueryWrapper<SysUser> qw = Wrappers.lambdaQuery();
        /** 用户管理仅面向学生账号，不包含管理员 / 食堂负责人 */
        qw.eq(SysUser::getRole, Roles.USER);
        String uid = userId == null ? null : userId.trim();
        if (uid != null && !uid.isEmpty()) {
            String like = "%" + escapeLike(uid) + "%";
            qw.nested(w -> w.like(SysUser::getUsername, uid).or().apply("CAST(id AS CHAR) LIKE {0}", like));
        }
        String constCode = constitutionCode == null ? null : constitutionCode.trim();
        if (constCode != null && !constCode.isEmpty()) {
            List<UserProfile> profs =
                    userProfileMapper.selectList(
                            Wrappers.<UserProfile>lambdaQuery()
                                    .eq(UserProfile::getConstitutionCode, constCode));
            Set<Long> ids =
                    profs.stream()
                            .map(UserProfile::getUserId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());
            if (ids.isEmpty()) {
                return ApiResponse.ok(new PageResult<>(List.of(), 0, page, pageSize, false));
            }
            qw.in(SysUser::getId, ids);
        }
        qw.orderByDesc(SysUser::getId);
        Page<SysUser> p = sysUserMapper.selectPage(new Page<>(page, pageSize), qw);
        List<Long> userIds = p.getRecords().stream().map(SysUser::getId).collect(Collectors.toList());
        Map<Long, UserProfile> profMap = loadProfiles(userIds);
        List<AdminUserListRowDto> rows =
                p.getRecords().stream()
                        .map(u -> toListRow(u, profMap.get(u.getId())))
                        .collect(Collectors.toList());
        return ApiResponse.ok(new PageResult<>(rows, p.getTotal(), page, pageSize, p.hasNext()));
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** 用户管理 API 仅允许操作学生（USER）行 */
    private static void assertStudentUserManageable(SysUser u) {
        if (u.getRole() == null || !Roles.USER.equalsIgnoreCase(u.getRole().trim())) {
            throw new BizException(400, "该账号不在学生用户管理范围内（管理员/食堂账号请使用系统账号维护）");
        }
    }

    private Map<Long, UserProfile> loadProfiles(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<UserProfile> list =
                userProfileMapper.selectList(
                        Wrappers.<UserProfile>lambdaQuery().in(UserProfile::getUserId, userIds));
        return list.stream().collect(Collectors.toMap(UserProfile::getUserId, x -> x, (a, b) -> a));
    }

    private AdminUserListRowDto toListRow(SysUser u, UserProfile p) {
        AdminUserListRowDto d = new AdminUserListRowDto();
        d.setId(String.valueOf(u.getId()));
        if (p != null && p.getConstitutionCode() != null && !p.getConstitutionCode().isBlank()) {
            d.setConstitutionCode(p.getConstitutionCode().trim());
            d.setConstitutionLabel(ConstitutionLabelUtil.labelForCode(p.getConstitutionCode().trim()));
        } else {
            d.setConstitutionCode("");
            d.setConstitutionLabel("未设置");
        }
        d.setRegisteredAt(u.getCreatedAt() != null ? u.getCreatedAt().toString() : null);
        d.setStatus(u.getStatus() != null && u.getStatus() == 0 ? "disabled" : "active");
        return d;
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable long id) {
        SecurityUtils.requireAdmin();
        SysUser u = sysUserMapper.selectById(id);
        if (u == null) {
            throw new BizException(404, "用户不存在");
        }
        assertStudentUserManageable(u);
        u.setPassword(null);
        UserProfile p = userProfileMapper.selectById(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(u.getId()));
        String code = p != null && p.getConstitutionCode() != null ? p.getConstitutionCode().trim() : "";
        m.put("constitutionCode", code);
        m.put("constitutionLabel", code.isEmpty() ? "未设置" : ConstitutionLabelUtil.labelForCode(code));
        m.put(
                "constitutionSource",
                p == null || p.getConstitutionSource() == null ? "unset" : p.getConstitutionSource());
        String seasonCode =
                p != null && p.getSeasonCode() != null && !p.getSeasonCode().isBlank()
                        ? p.getSeasonCode().trim()
                        : "spring";
        m.put("seasonCode", seasonCode);
        m.put("seasonLabel", labelSeason(seasonCode));
        m.put("status", u.getStatus() != null && u.getStatus() == 0 ? "disabled" : "active");
        m.put("registeredAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : null);
        m.put("surveyScores", parseSurveyScores(p));
        m.put("favorites", buildFavorites(id));
        return ApiResponse.ok(m);
    }

    private static String labelSeason(String code) {
        if (code == null) {
            return "";
        }
        switch (code.toLowerCase(Locale.ROOT)) {
            case "spring":
                return "春";
            case "summer":
                return "夏";
            case "autumn":
                return "秋";
            case "winter":
                return "冬";
            default:
                return code;
        }
    }

    private Map<String, Object> parseSurveyScores(UserProfile p) {
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("constitutionTendency", Map.of());
        empty.put("groupAverages", Map.of());
        empty.put("submittedAt", null);
        if (p == null || p.getSurveyScoresJson() == null || p.getSurveyScoresJson().isBlank()) {
            return empty;
        }
        try {
            JsonNode n = objectMapper.readTree(p.getSurveyScoresJson());
            if (!n.isObject()) {
                return empty;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.convertValue(n, Map.class);
            return parsed;
        } catch (Exception e) {
            return empty;
        }
    }

    private Map<String, Object> buildFavorites(long userId) {
        List<UserFavorite> favs =
                userFavoriteMapper.selectList(
                        Wrappers.<UserFavorite>lambdaQuery()
                                .eq(UserFavorite::getUserId, userId)
                                .orderByDesc(UserFavorite::getCreatedAt)
                                .last("LIMIT 50"));
        List<Map<String, Object>> recipes = new ArrayList<>();
        for (UserFavorite f : favs) {
            var r = recipeMapper.selectById(f.getRecipeId());
            if (r == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("recipeId", String.valueOf(r.getId()));
            row.put("name", r.getName());
            row.put("favoritedAt", f.getCreatedAt() != null ? f.getCreatedAt().toString() : null);
            recipes.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("recipes", recipes);
        out.put("aiPlans", List.of());
        return out;
    }

    @PatchMapping("/{id}")
    public ApiResponse<AdminUserListRowDto> patchStatus(
            @PathVariable long id, @RequestBody StatusPatch body) {
        SecurityUtils.requireAdmin();
        if (body == null || body.getStatus() == null) {
            throw new BizException(400, "缺少 status");
        }
        String s = body.getStatus().trim().toLowerCase(Locale.ROOT);
        if (!"active".equals(s) && !"disabled".equals(s)) {
            throw new BizException(400, "无效的状态值");
        }
        SysUser u = sysUserMapper.selectById(id);
        if (u == null) {
            throw new BizException(404, "用户不存在");
        }
        assertStudentUserManageable(u);
        u.setStatus("disabled".equals(s) ? 0 : 1);
        sysUserMapper.updateById(u);
        UserProfile p = userProfileMapper.selectById(id);
        return ApiResponse.ok(toListRow(sysUserMapper.selectById(id), p));
    }

    @PutMapping("/{id}")
    public ApiResponse<SysUser> update(@PathVariable long id, @RequestBody UserPatch body) {
        SecurityUtils.requireAdmin();
        if (body == null) {
            throw new BizException(400, "缺少请求体");
        }
        SysUser u = sysUserMapper.selectById(id);
        if (u == null) {
            throw new BizException(404, "用户不存在");
        }
        assertStudentUserManageable(u);
        if (body.getRole() != null) {
            String nextRole = body.getRole().trim().toUpperCase(Locale.ROOT);
            if (!Roles.USER.equals(nextRole)) {
                throw new BizException(400, "学生用户管理接口不允许变更角色");
            }
            u.setRole(Roles.USER);
        }
        if (body.getStatus() != null) {
            u.setStatus(body.getStatus());
        }
        sysUserMapper.updateById(u);
        u.setPassword(null);
        return ApiResponse.ok(u);
    }

    @Data
    public static class StatusPatch {
        private String status;
    }

    @Data
    public static class UserPatch {
        private String role;
        private Integer status;
    }
}

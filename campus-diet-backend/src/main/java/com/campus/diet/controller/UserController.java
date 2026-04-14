package com.campus.diet.controller;

import com.campus.diet.common.ApiResponse;
import com.campus.diet.entity.SysUser;
import com.campus.diet.entity.UserProfile;
import com.campus.diet.mapper.SysUserMapper;
import com.campus.diet.security.LoginUser;
import com.campus.diet.security.SecurityUtils;
import com.campus.diet.service.ConstitutionSurveyService;
import com.campus.diet.service.UserProfileService;
import lombok.Data;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserProfileService userProfileService;
    private final ConstitutionSurveyService constitutionSurveyService;
    private final SysUserMapper sysUserMapper;

    public UserController(
            UserProfileService userProfileService,
            ConstitutionSurveyService constitutionSurveyService,
            SysUserMapper sysUserMapper) {
        this.userProfileService = userProfileService;
        this.constitutionSurveyService = constitutionSurveyService;
        this.sysUserMapper = sysUserMapper;
    }

    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> profile() {
        LoginUser u = SecurityUtils.requireLogin();
        SysUser su = sysUserMapper.selectById(u.getUserId());
        UserProfile p = userProfileService.require(u.getUserId());
        Map<String, Object> m = new HashMap<>();
        m.put("userId", u.getUserId());
        m.put("username", su != null ? su.getUsername() : u.getUsername());
        m.put("role", su != null ? su.getRole() : u.getRole());
        m.put("constitutionCode", p.getConstitutionCode());
        m.put("constitutionSource", p.getConstitutionSource());
        m.put("seasonCode", p.getSeasonCode());
        m.put("recommendEnabled", p.getRecommendEnabled() != null && p.getRecommendEnabled() == 1);
        m.put("dataCollectionEnabled", p.getDataCollectionEnabled() != null && p.getDataCollectionEnabled() == 1);
        m.put("surveyScoresJson", p.getSurveyScoresJson());
        return ApiResponse.ok(m);
    }

    /**
     * 手动更新体质（联调脚本 PATH_USER_PROFILE_PUT）；{@code constitution} 中文标签当前仅作占位，以 code 为准。
     */
    @PutMapping("/profile")
    public ApiResponse<Map<String, Object>> updateProfile(@RequestBody ProfilePutBody body) {
        LoginUser u = SecurityUtils.requireLogin();
        if (body.getConstitutionCode() != null && !body.getConstitutionCode().isBlank()) {
            userProfileService.updateConstitutionManual(u.getUserId(), body.getConstitutionCode());
        }
        Map<String, Object> m = new HashMap<>();
        m.put("ok", true);
        return ApiResponse.ok(m);
    }

    @PutMapping("/preferences")
    public ApiResponse<Map<String, Object>> preferences(@RequestBody PrefBody body) {
        LoginUser u = SecurityUtils.requireLogin();
        if (body.getDataCollectionEnabled() != null) {
            userProfileService.updatePrivacy(u.getUserId(), body.getDataCollectionEnabled());
        }
        if (body.getRecommendEnabled() != null) {
            userProfileService.updateRecommendSwitch(u.getUserId(), body.getRecommendEnabled());
        }
        Map<String, Object> m = new HashMap<>();
        m.put("ok", true);
        return ApiResponse.ok(m);
    }

    @PostMapping("/constitution/survey")
    public ApiResponse<Map<String, Object>> submitSurvey(@Valid @RequestBody SurveyBody body) {
        LoginUser u = SecurityUtils.requireLogin();
        ConstitutionSurveyService.SurveyResult r = constitutionSurveyService.evaluate(body.getAnswers());
        userProfileService.saveConstitution(u.getUserId(), r.primaryCode, body.getSeasonCode(), r.scoresJson);
        Map<String, Object> m = new HashMap<>();
        m.put("primaryCode", r.primaryCode);
        m.put("primaryLabel", r.primaryLabel);
        m.put("scores", r.scores);
        return ApiResponse.ok(m);
    }

    @DeleteMapping("/constitution")
    public ApiResponse<Map<String, Object>> clearConstitution() {
        LoginUser u = SecurityUtils.requireLogin();
        userProfileService.clearConstitution(u.getUserId());
        Map<String, Object> m = new HashMap<>();
        m.put("ok", true);
        return ApiResponse.ok(m);
    }

    @Data
    public static class PrefBody {
        private Boolean dataCollectionEnabled;
        private Boolean recommendEnabled;
    }

    @Data
    public static class ProfilePutBody {
        private String constitutionCode;
        /** 可选，如「阴虚质」；服务端以 constitutionCode 为准 */
        private String constitution;
    }

    @Data
    public static class SurveyBody {
        @NotNull
        @Size(min = 9, max = 9, message = "问卷需 9 题得分")
        private List<Integer> answers;
        private String seasonCode;
    }
}

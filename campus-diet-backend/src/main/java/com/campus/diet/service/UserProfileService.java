package com.campus.diet.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.diet.entity.UserProfile;
import com.campus.diet.mapper.UserProfileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {

    private final UserProfileMapper userProfileMapper;

    public UserProfileService(UserProfileMapper userProfileMapper) {
        this.userProfileMapper = userProfileMapper;
    }

    public UserProfile require(long userId) {
        UserProfile p = userProfileMapper.selectById(userId);
        if (p == null) {
            UserProfile created = new UserProfile();
            created.setUserId(userId);
            created.setRecommendEnabled(1);
            created.setDataCollectionEnabled(1);
            created.setSeasonCode(SeasonUtil.currentSeasonCode(java.time.LocalDate.now()));
            userProfileMapper.insert(created);
            return created;
        }
        return p;
    }

    @Transactional
    public void updatePrivacy(long userId, boolean dataCollectionEnabled) {
        UserProfile p = require(userId);
        p.setDataCollectionEnabled(dataCollectionEnabled ? 1 : 0);
        userProfileMapper.updateById(p);
    }

    @Transactional
    public void updateRecommendSwitch(long userId, boolean recommendEnabled) {
        UserProfile p = require(userId);
        p.setRecommendEnabled(recommendEnabled ? 1 : 0);
        userProfileMapper.updateById(p);
    }

    @Transactional
    public void saveConstitution(long userId, String constitutionCode, String seasonCode, String scoresJson) {
        UserProfile p = require(userId);
        p.setConstitutionCode(constitutionCode);
        p.setConstitutionSource("survey");
        if (seasonCode != null && !seasonCode.isBlank()) {
            p.setSeasonCode(seasonCode);
        }
        if (scoresJson != null) {
            p.setSurveyScoresJson(scoresJson);
        }
        userProfileMapper.updateById(p);
    }

    public void clearConstitution(long userId) {
        UserProfile p = require(userId);
        p.setConstitutionCode(null);
        p.setConstitutionSource(null);
        p.setSurveyScoresJson(null);
        userProfileMapper.updateById(p);
    }

    /** 联调 / PRD：PUT 用户资料时手动指定体质 code（非问卷路径） */
    @Transactional
    public void updateConstitutionManual(long userId, String constitutionCode) {
        if (constitutionCode == null || constitutionCode.isBlank()) {
            return;
        }
        UserProfile p = require(userId);
        p.setConstitutionCode(constitutionCode.trim());
        p.setConstitutionSource("manual");
        userProfileMapper.updateById(p);
    }
}

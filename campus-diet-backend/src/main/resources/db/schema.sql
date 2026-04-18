SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) NOT NULL,
  `password` varchar(128) NOT NULL,
  `role` varchar(32) NOT NULL DEFAULT 'USER' COMMENT 'USER,ADMIN,CANTEEN_MANAGER',
  `status` tinyint NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_profile` (
  `user_id` bigint NOT NULL,
  `constitution_code` varchar(32) DEFAULT NULL,
  `constitution_source` varchar(32) DEFAULT NULL,
  `season_code` varchar(16) DEFAULT NULL,
  `survey_scores_json` text COMMENT '问卷各维度得分 JSON',
  `recommend_enabled` tinyint NOT NULL DEFAULT 1,
  `data_collection_enabled` tinyint NOT NULL DEFAULT 1,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_profile_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `campus_scene` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `icon` varchar(16) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `tags_json` json DEFAULT NULL,
  `extra_json` json DEFAULT NULL COMMENT '场景扩展：痛点、茶饮方、禁忌、食材解读等',
  `sort_order` int NOT NULL DEFAULT 0,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `recipe` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL,
  `cover_url` varchar(512) DEFAULT NULL,
  `efficacy_summary` varchar(255) DEFAULT NULL,
  `collect_count` int NOT NULL DEFAULT 0,
  `season_tags` varchar(128) DEFAULT NULL COMMENT 'spring,summer,autumn,winter 逗号分隔',
  `constitution_tags` varchar(255) DEFAULT NULL COMMENT '体质 code 逗号分隔',
  `efficacy_tags` varchar(255) DEFAULT NULL COMMENT '功效标签',
  `symptom_tags` varchar(512) DEFAULT NULL COMMENT '适用症状/痛点关键词，逗号分隔，用于场景动态匹配',
  `instruction_summary` text,
  `steps_json` json DEFAULT NULL,
  `contraindication` text,
  `status` tinyint NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX `idx_recipe_status_deleted_collect` ON `recipe` (`status`,`deleted`,`collect_count`,`id`);

CREATE TABLE IF NOT EXISTS `scene_recipe` (
  `scene_id` bigint NOT NULL,
  `recipe_id` bigint NOT NULL,
  PRIMARY KEY (`scene_id`,`recipe_id`),
  KEY `idx_recipe` (`recipe_id`),
  CONSTRAINT `fk_sr_scene` FOREIGN KEY (`scene_id`) REFERENCES `campus_scene` (`id`),
  CONSTRAINT `fk_sr_recipe` FOREIGN KEY (`recipe_id`) REFERENCES `recipe` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ingredient` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `category` varchar(64) DEFAULT NULL,
  `note` varchar(512) DEFAULT NULL,
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否在药膳表单与下拉中启用',
  `image_url` varchar(512) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `recipe_ingredient` (
  `recipe_id` bigint NOT NULL,
  `ingredient_id` bigint NOT NULL,
  `amount_text` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`recipe_id`,`ingredient_id`),
  CONSTRAINT `fk_ri_recipe` FOREIGN KEY (`recipe_id`) REFERENCES `recipe` (`id`),
  CONSTRAINT `fk_ri_ing` FOREIGN KEY (`ingredient_id`) REFERENCES `ingredient` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `recipe_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_recipe` (`user_id`,`recipe_id`),
  KEY `idx_recipe` (`recipe_id`),
  CONSTRAINT `fk_fav_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_fav_recipe` FOREIGN KEY (`recipe_id`) REFERENCES `recipe` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `browse_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `recipe_id` bigint NOT NULL,
  `viewed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`viewed_at`),
  KEY `idx_user_recipe` (`user_id`,`recipe_id`),
  CONSTRAINT `fk_hist_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_hist_recipe` FOREIGN KEY (`recipe_id`) REFERENCES `recipe` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX `idx_profile_constitution` ON `user_profile` (`constitution_code`);

CREATE TABLE IF NOT EXISTS `feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `content` text NOT NULL,
  `source` varchar(64) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `system_kv` (
  `k` varchar(64) NOT NULL,
  `v` varchar(512) NOT NULL,
  PRIMARY KEY (`k`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_issue_sample` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `symptom` varchar(255) DEFAULT NULL,
  `constitution_code` varchar(32) DEFAULT NULL,
  `quality_score` int NOT NULL,
  `score_threshold` int NOT NULL,
  `safety_passed` tinyint NOT NULL DEFAULT 1,
  `violated_rules_json` text,
  `request_payload_json` text,
  `response_payload_json` longtext,
  `guard_enabled` tinyint NOT NULL DEFAULT 1,
  `strict_safety` tinyint NOT NULL DEFAULT 1,
  `source` varchar(64) DEFAULT NULL,
  `replayed` tinyint NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_issue_created` (`created_at`),
  KEY `idx_ai_issue_replayed` (`replayed`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `campus_canteen` (
  `id` varchar(32) NOT NULL,
  `campus_name` varchar(64) NOT NULL DEFAULT '',
  `display_name` varchar(128) NOT NULL,
  `sort_order` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `campus_weekly_calendar` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `week_monday` date NOT NULL COMMENT 'ISO 周起始周一（Asia/Shanghai）',
  `canteen_id` varchar(32) NOT NULL,
  `published` tinyint NOT NULL DEFAULT 0,
  `week_title` varchar(255) DEFAULT NULL,
  `estimated_publish_note` varchar(512) DEFAULT NULL,
  `days_json` json NOT NULL COMMENT 'CalendarDay[] 与前端 WeeklyCalendarPayload.days 对齐',
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_week_canteen` (`week_monday`,`canteen_id`),
  KEY `idx_canteen` (`canteen_id`),
  CONSTRAINT `fk_cal_canteen` FOREIGN KEY (`canteen_id`) REFERENCES `campus_canteen` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

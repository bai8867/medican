-- 兼容已存在库：若未执行新版 schema.sql，可单独补表（幂等）
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
  `days_json` json NOT NULL,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_week_canteen` (`week_monday`,`canteen_id`),
  KEY `idx_canteen` (`canteen_id`),
  CONSTRAINT `fk_cal_canteen` FOREIGN KEY (`canteen_id`) REFERENCES `campus_canteen` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

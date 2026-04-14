-- 兼容已存在库：新列若已存在则语句失败，由 spring.sql.init.continue-on-error 忽略
ALTER TABLE `campus_scene` ADD COLUMN `extra_json` json DEFAULT NULL COMMENT '场景扩展：痛点、茶饮方、禁忌、食材解读等';
ALTER TABLE `recipe` ADD COLUMN `symptom_tags` varchar(512) DEFAULT NULL COMMENT '适用症状/痛点关键词，逗号分隔，用于场景动态匹配';

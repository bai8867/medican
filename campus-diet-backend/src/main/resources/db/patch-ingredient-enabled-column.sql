-- 兼容已存在库：列已存在时失败由 spring.sql.init.continue-on-error 忽略
ALTER TABLE `ingredient` ADD COLUMN `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否在药膳表单与下拉中启用';

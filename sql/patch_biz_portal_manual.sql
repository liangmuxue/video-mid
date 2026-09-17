-- =============================================================================
-- 业务端补丁 · 手工版（无 PREPARE，适合 Navicat / DBeaver 逐条执行）
-- 请先 USE video_mid;  某条报 Duplicate column name / Duplicate key 可跳过
-- =============================================================================

USE `video_mid`;

-- 【必做-1】创建设备目录表
CREATE TABLE IF NOT EXISTS `device_folder` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `parent_id`     BIGINT       DEFAULT NULL COMMENT '父目录，根为 NULL',
  `name`          VARCHAR(128) NOT NULL,
  `sort_no`       INT          NOT NULL DEFAULT 0,
  `path`          VARCHAR(512) DEFAULT NULL COMMENT '物化路径 /1/3/8/',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_path` (`path`(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备目录';

-- 【必做-2】device 增加 folder_id（若已存在会报错，可忽略继续下一条）
ALTER TABLE `device`
  ADD COLUMN `folder_id` BIGINT DEFAULT NULL COMMENT '所属目录' AFTER `platform_id`;

-- 【必做-3】folder_id 索引（若已存在会报错，可忽略）
ALTER TABLE `device` ADD KEY `idx_folder_id` (`folder_id`);

-- 【必做-4】码流直播标记（若已存在会报错，可忽略）
ALTER TABLE `device_stream`
  ADD COLUMN `live_enabled` TINYINT NOT NULL DEFAULT 0 COMMENT '业务端直播 0/1' AFTER `sort_no`;

-- 【可选-5】默认根目录
INSERT INTO `device_folder` (`parent_id`, `name`, `sort_no`, `path`)
SELECT NULL, '默认目录', 0, NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `device_folder` LIMIT 1);

-- 【可选-6】状态兼容
UPDATE `device` SET status = '已启用' WHERE status IN ('ON', 'on', 'ENABLED');
UPDATE `device` SET status = '已停用' WHERE status IN ('OFF', 'off', 'DISABLED');

-- 【校验】应返回 1
SELECT COUNT(*) AS device_folder_table_exists
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'video_mid' AND TABLE_NAME = 'device_folder';

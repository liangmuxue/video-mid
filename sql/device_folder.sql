-- 设备目录树 + device.folder_id（已有库执行；可重复执行）
USE `video_mid`;

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
  KEY `idx_path` (`path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备目录';

SET @col_exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device' AND COLUMN_NAME = 'folder_id'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `device` ADD COLUMN `folder_id` BIGINT DEFAULT NULL COMMENT ''所属目录'' AFTER `platform_id`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(1) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device' AND INDEX_NAME = 'idx_folder_id'
);
SET @sql2 := IF(@idx_exists = 0,
  'ALTER TABLE `device` ADD KEY `idx_folder_id` (`folder_id`)',
  'SELECT 1');
PREPARE stmt2 FROM @sql2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

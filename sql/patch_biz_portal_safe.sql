-- =============================================================================
-- 业务端 / 设备目录 / 直播 一键补丁（可重复执行，尽量不因已存在而报错）
-- 用法：在 MySQL 客户端选中库 video_mid 后整段执行
-- 若 GUI 工具对 PREPARE 报错，请改用 patch_biz_portal_manual.sql 逐条执行
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `video_mid`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `video_mid`;

-- ---------------------------------------------------------------------------
-- 1. 设备目录表（业务端左侧树）
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- 2. device.folder_id（显式 schema，避免 DATABASE() 选错库）
-- ---------------------------------------------------------------------------
SET @col_exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'video_mid' AND TABLE_NAME = 'device' AND COLUMN_NAME = 'folder_id'
);
SET @has_platform := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'video_mid' AND TABLE_NAME = 'device' AND COLUMN_NAME = 'platform_id'
);
SET @sql := IF(@col_exists = 0,
  IF(@has_platform > 0,
    'ALTER TABLE `video_mid`.`device` ADD COLUMN `folder_id` BIGINT DEFAULT NULL COMMENT ''所属目录'' AFTER `platform_id`',
    'ALTER TABLE `video_mid`.`device` ADD COLUMN `folder_id` BIGINT DEFAULT NULL COMMENT ''所属目录'''),
  'SELECT ''skip folder_id'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(1) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = 'video_mid' AND TABLE_NAME = 'device' AND INDEX_NAME = 'idx_folder_id'
);
SET @sql2 := IF(@idx_exists = 0,
  'ALTER TABLE `video_mid`.`device` ADD KEY `idx_folder_id` (`folder_id`)',
  'SELECT ''skip idx_folder_id'' AS msg');
PREPARE stmt2 FROM @sql2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

-- ---------------------------------------------------------------------------
-- 3. device_stream.live_enabled（业务直播标记）
-- ---------------------------------------------------------------------------
SET @live_col := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'video_mid' AND TABLE_NAME = 'device_stream' AND COLUMN_NAME = 'live_enabled'
);
SET @sql3 := IF(@live_col = 0,
  'ALTER TABLE `video_mid`.`device_stream` ADD COLUMN `live_enabled` TINYINT NOT NULL DEFAULT 0 COMMENT ''业务端直播 0/1'' AFTER `sort_no`',
  'SELECT ''skip live_enabled'' AS msg');
PREPARE stmt3 FROM @sql3; EXECUTE stmt3; DEALLOCATE PREPARE stmt3;

-- 每设备默认选一路直播：优先 sub，否则 main（仅对尚无 live_enabled=1 的设备）
UPDATE `video_mid`.`device_stream` ds
INNER JOIN (
  SELECT device_id,
         COALESCE(
           MAX(CASE WHEN stream_type = 'sub' THEN id END),
           MAX(CASE WHEN stream_type = 'main' THEN id END)
         ) AS pick_id
  FROM `video_mid`.`device_stream`
  GROUP BY device_id
) t ON ds.device_id = t.device_id AND ds.id = t.pick_id
SET ds.live_enabled = 1
WHERE NOT EXISTS (
  SELECT 1 FROM `video_mid`.`device_stream` x
  WHERE x.device_id = ds.device_id AND x.live_enabled = 1
);

-- ---------------------------------------------------------------------------
-- 4. 设备状态三态（兼容历史 ON/OFF，失败可忽略）
-- ---------------------------------------------------------------------------
UPDATE `video_mid`.`device` SET status = '已启用' WHERE status IN ('ON', 'on', 'ENABLED');
UPDATE `video_mid`.`device` SET status = '已停用' WHERE status IN ('OFF', 'off', 'DISABLED');
UPDATE `video_mid`.`device` SET status = '不可用' WHERE status IN ('UNAVAILABLE', 'unavailable');

-- ---------------------------------------------------------------------------
-- 5. 可选：插入一个根目录，避免树完全为空（按需保留）
-- ---------------------------------------------------------------------------
INSERT INTO `video_mid`.`device_folder` (`parent_id`, `name`, `sort_no`, `path`)
SELECT NULL, '默认目录', 0, NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `video_mid`.`device_folder` LIMIT 1);

-- ---------------------------------------------------------------------------
-- 6. 校验（执行后应看到 device_folder 表）
-- ---------------------------------------------------------------------------
SELECT 'device_folder' AS check_item,
       COUNT(*) AS row_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'video_mid' AND TABLE_NAME = 'device_folder';

SELECT 'device.folder_id' AS check_item,
       COUNT(*) AS exists_flag
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'video_mid' AND TABLE_NAME = 'device' AND COLUMN_NAME = 'folder_id';

SELECT 'device_stream.live_enabled' AS check_item,
       COUNT(*) AS exists_flag
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'video_mid' AND TABLE_NAME = 'device_stream' AND COLUMN_NAME = 'live_enabled';

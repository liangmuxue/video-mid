-- 码流增加业务端直播标记（每设备最多一路 live_enabled=1；默认优先 sub）
USE `video_mid`;

SET @col_exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_stream' AND COLUMN_NAME = 'live_enabled'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `device_stream` ADD COLUMN `live_enabled` TINYINT NOT NULL DEFAULT 0 COMMENT ''业务端直播 0/1'' AFTER `sort_no`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 每个设备默认选一路直播：优先 sub，否则 main
UPDATE device_stream ds
INNER JOIN (
  SELECT device_id,
         COALESCE(
           MAX(CASE WHEN stream_type = 'sub' THEN id END),
           MAX(CASE WHEN stream_type = 'main' THEN id END)
         ) AS pick_id
  FROM device_stream
  GROUP BY device_id
) t ON ds.device_id = t.device_id AND ds.id = t.pick_id
SET ds.live_enabled = 1
WHERE NOT EXISTS (
  SELECT 1 FROM device_stream x WHERE x.device_id = ds.device_id AND x.live_enabled = 1
);

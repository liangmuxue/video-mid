-- 宇视模拟设备种子数据（与 src/main/resources/uniview/mock-devices.json 保持一致）
-- 在 Navicat 执行前请确认使用 video_mid 库；可重复执行（按 device_id  upsert 思路需手动删旧数据）

USE `video_mid`;

SET @now_ms = UNIX_TIMESTAMP(NOW(3)) * 1000;

-- 可选：先删除旧模拟设备
DELETE FROM `device_stream` WHERE `device_id` IN ('TIC7632_01', 'TIC7632_02');
DELETE FROM `device` WHERE `device_id` IN ('TIC7632_01', 'TIC7632_02');

INSERT INTO `device`
  (`device_id`, `name`, `platform_id`, `folder_id`, `status`, `manufacturer`, `model`, `address`, `ptz_type`, `gateway_id`, `longitude`, `latitude`, `created_at`, `updated_at`)
VALUES
('TIC7632_01', '观测云台-东门（模拟）', '34020000002000000001', NULL, 1, '宇视', 'TIC7632-IRL@L-F75-4X56-GB-VH1', '小区东门制高点', 1, NULL, 116.40, 39.90, @now_ms, @now_ms),
('TIC7632_02', '观测云台-西区（模拟）', '34020000002000000001', NULL, 1, '宇视', 'TIC7632-IRL@L-F75-4X56-GB-VH1', '西区瞭望塔', 1, NULL, 116.39, 39.91, @now_ms, @now_ms);

INSERT INTO `device_stream`
  (`device_id`, `stream_type`, `channel_id`, `stream_url`, `stream_name`, `status`, `sort_no`, `live_enabled`, `created_at`, `updated_at`)
VALUES
('TIC7632_01', 'visible_main', '34020000001320000001', 'http://8.130.74.232:8080/live/tic7632_01_visible_main.live.flv', '可见光-主码流', 'ON', 1, 0, @now_ms, @now_ms),
('TIC7632_01', 'visible_sub',  '34020000001320000002', 'http://8.130.74.232:8080/live/tic7632_01_visible_sub.live.flv',  '可见光-子码流', 'ON', 2, 1, @now_ms, @now_ms),
('TIC7632_01', 'thermal_main', '34020000001320000003', 'http://8.130.74.232:8080/live/tic7632_01_thermal_main.live.flv', '热成像-主码流', 'ON', 3, 0, @now_ms, @now_ms),
('TIC7632_02', 'visible_main', '34020000001320000011', 'http://8.130.74.232:8080/live/tic7632_02_visible_main.live.flv', '可见光-主码流', 'ON', 1, 0, @now_ms, @now_ms),
('TIC7632_02', 'thermal_main', '34020000001320000012', 'http://8.130.74.232:8080/live/tic7632_02_thermal_main.live.flv', '热成像-主码流', 'ON', 2, 1, @now_ms, @now_ms);

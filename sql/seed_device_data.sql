-- 仅初始化演示数据（表已存在时执行）
USE `video_mid`;

INSERT INTO `device` (`device_id`, `name`, `platform_id`, `status`, `manufacturer`, `model`, `address`, `ptz_type`, `gateway_id`)
SELECT * FROM (
  SELECT 'CAM_EAST_01' AS device_id, '东门球机' AS name, '34020000002000000001' AS platform_id, 'ON' AS status,
         '宇视' AS manufacturer, 'IPC-B系列' AS model, '小区东门' AS address, 1 AS ptz_type, 'GW_COMMUNITY_01' AS gateway_id
) t WHERE NOT EXISTS (SELECT 1 FROM `device` WHERE `device_id` = 'CAM_EAST_01');

INSERT INTO `device` (`device_id`, `name`, `platform_id`, `status`, `manufacturer`, `model`, `address`, `ptz_type`, `gateway_id`)
SELECT * FROM (
  SELECT 'CAM_GATE_02' AS device_id, '岗卡枪机' AS name, '34020000002000000001' AS platform_id, 'ON' AS status,
         '宇视' AS manufacturer, 'IPC-B系列' AS model, '小区岗卡' AS address, 0 AS ptz_type, 'GW_COMMUNITY_01' AS gateway_id
) t WHERE NOT EXISTS (SELECT 1 FROM `device` WHERE `device_id` = 'CAM_GATE_02');

INSERT INTO `device` (`device_id`, `name`, `platform_id`, `status`, `manufacturer`, `model`, `address`, `ptz_type`, `gateway_id`)
SELECT * FROM (
  SELECT 'CAM_PARK_03' AS device_id, '停车场半球' AS name, '34020000002000000001' AS platform_id, 'ON' AS status,
         '海康' AS manufacturer, 'DS-2CD' AS model, '地下车库入口' AS address, 0 AS ptz_type, NULL AS gateway_id
) t WHERE NOT EXISTS (SELECT 1 FROM `device` WHERE `device_id` = 'CAM_PARK_03');

INSERT INTO `device_stream` (`device_id`, `stream_type`, `channel_id`, `stream_url`, `stream_name`, `status`, `sort_no`)
SELECT * FROM (
  SELECT 'CAM_EAST_01' AS device_id, 'main' AS stream_type, '34020000001320000001' AS channel_id,
         'http://8.130.74.232:80/rtp/CAM_EAST_01_main.live.flv' AS stream_url, '东门-主码流' AS stream_name, 'ON' AS status, 1 AS sort_no
) t WHERE NOT EXISTS (SELECT 1 FROM `device_stream` WHERE `device_id` = 'CAM_EAST_01' AND `stream_type` = 'main');

INSERT INTO `device_stream` (`device_id`, `stream_type`, `channel_id`, `stream_url`, `stream_name`, `status`, `sort_no`)
SELECT * FROM (
  SELECT 'CAM_EAST_01' AS device_id, 'sub' AS stream_type, '34020000001320000002' AS channel_id,
         'http://8.130.74.232:80/rtp/CAM_EAST_01_sub.live.flv' AS stream_url, '东门-子码流' AS stream_name, 'ON' AS status, 2 AS sort_no
) t WHERE NOT EXISTS (SELECT 1 FROM `device_stream` WHERE `device_id` = 'CAM_EAST_01' AND `stream_type` = 'sub');

INSERT INTO `device_stream` (`device_id`, `stream_type`, `channel_id`, `stream_url`, `stream_name`, `status`, `sort_no`)
SELECT * FROM (
  SELECT 'CAM_GATE_02' AS device_id, 'main' AS stream_type, '34020000001320000003' AS channel_id,
         'http://8.130.74.232:80/rtp/CAM_GATE_02_main.live.flv' AS stream_url, '岗卡-主码流' AS stream_name, 'ON' AS status, 1 AS sort_no
) t WHERE NOT EXISTS (SELECT 1 FROM `device_stream` WHERE `device_id` = 'CAM_GATE_02' AND `stream_type` = 'main');

INSERT INTO `device_stream` (`device_id`, `stream_type`, `channel_id`, `stream_url`, `stream_name`, `status`, `sort_no`)
SELECT * FROM (
  SELECT 'CAM_GATE_02' AS device_id, 'sub' AS stream_type, '34020000001320000004' AS channel_id,
         'http://8.130.74.232:80/rtp/CAM_GATE_02_sub.live.flv' AS stream_url, '岗卡-子码流' AS stream_name, 'ON' AS status, 2 AS sort_no
) t WHERE NOT EXISTS (SELECT 1 FROM `device_stream` WHERE `device_id` = 'CAM_GATE_02' AND `stream_type` = 'sub');

INSERT INTO `device_stream` (`device_id`, `stream_type`, `channel_id`, `stream_url`, `stream_name`, `status`, `sort_no`)
SELECT * FROM (
  SELECT 'CAM_PARK_03' AS device_id, 'sub' AS stream_type, '34020000001320000005' AS channel_id,
         'http://8.130.74.232:80/rtp/CAM_PARK_03_sub.live.flv' AS stream_url, '停车场-子码流' AS stream_name, 'ON' AS status, 1 AS sort_no
) t WHERE NOT EXISTS (SELECT 1 FROM `device_stream` WHERE `device_id` = 'CAM_PARK_03' AND `stream_type` = 'sub');

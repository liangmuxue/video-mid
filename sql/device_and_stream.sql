-- 设备/码流表 + 初始数据（已有库可执行）
USE `video_mid`;

CREATE TABLE IF NOT EXISTS `device` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `device_id`     VARCHAR(64)  NOT NULL COMMENT '业务设备唯一ID',
  `name`          VARCHAR(256) DEFAULT NULL COMMENT '设备名称',
  `platform_id`   VARCHAR(64)  DEFAULT NULL COMMENT '下级平台国标ID',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'OFF' COMMENT 'ON/OFF',
  `manufacturer`  VARCHAR(128) DEFAULT NULL COMMENT '厂家',
  `model`         VARCHAR(128) DEFAULT NULL COMMENT '型号',
  `address`       VARCHAR(256) DEFAULT NULL COMMENT '安装地址',
  `ptz_type`      INT          NOT NULL DEFAULT 0 COMMENT '云台类型：0无',
  `gateway_id`    VARCHAR(64)  DEFAULT NULL COMMENT '边缘网关ID',
  `longitude`     DOUBLE       DEFAULT NULL COMMENT '经度',
  `latitude`      DOUBLE       DEFAULT NULL COMMENT '纬度',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_id` (`device_id`),
  KEY `idx_platform_id` (`platform_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频设备';

CREATE TABLE IF NOT EXISTS `device_stream` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `device_id`     VARCHAR(64)  NOT NULL COMMENT '关联 device.device_id',
  `stream_type`   VARCHAR(16)  NOT NULL COMMENT 'main/sub',
  `channel_id`    VARCHAR(64)  DEFAULT NULL COMMENT '国标通道编码（预留，暂不用于取流）',
  `stream_url`    VARCHAR(512) DEFAULT NULL COMMENT '码流播放地址（注册获得）',
  `stream_name`   VARCHAR(256) DEFAULT NULL COMMENT '码流名称',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'OFF' COMMENT 'ON/OFF',
  `sort_no`       INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_stream` (`device_id`, `stream_type`),
  UNIQUE KEY `uk_channel_id` (`channel_id`),
  KEY `idx_device_id` (`device_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备码流';

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
         'rtsp://127.0.0.1:8554/live/cam01_main' AS stream_url, '东门-主码流' AS stream_name, 'ON' AS status, 1 AS sort_no
) t WHERE NOT EXISTS (SELECT 1 FROM `device_stream` WHERE `device_id` = 'CAM_EAST_01' AND `stream_type` = 'main');

INSERT INTO `device_stream` (`device_id`, `stream_type`, `channel_id`, `stream_url`, `stream_name`, `status`, `sort_no`)
SELECT * FROM (
  SELECT 'CAM_EAST_01' AS device_id, 'sub' AS stream_type, '34020000001320000002' AS channel_id,
         'rtsp://127.0.0.1:8554/live/cam01_sub' AS stream_url, '东门-子码流' AS stream_name, 'ON' AS status, 2 AS sort_no
) t WHERE NOT EXISTS (SELECT 1 FROM `device_stream` WHERE `device_id` = 'CAM_EAST_01' AND `stream_type` = 'sub');

INSERT INTO `device_stream` (`device_id`, `stream_type`, `channel_id`, `stream_url`, `stream_name`, `status`, `sort_no`)
SELECT * FROM (
  SELECT 'CAM_GATE_02' AS device_id, 'main' AS stream_type, '34020000001320000003' AS channel_id,
         'rtsp://127.0.0.1:8554/live/cam02_main' AS stream_url, '岗卡-主码流' AS stream_name, 'ON' AS status, 1 AS sort_no
) t WHERE NOT EXISTS (SELECT 1 FROM `device_stream` WHERE `device_id` = 'CAM_GATE_02' AND `stream_type` = 'main');

INSERT INTO `device_stream` (`device_id`, `stream_type`, `channel_id`, `stream_url`, `stream_name`, `status`, `sort_no`)
SELECT * FROM (
  SELECT 'CAM_GATE_02' AS device_id, 'sub' AS stream_type, '34020000001320000004' AS channel_id,
         'rtsp://127.0.0.1:8554/live/cam02_sub' AS stream_url, '岗卡-子码流' AS stream_name, 'ON' AS status, 2 AS sort_no
) t WHERE NOT EXISTS (SELECT 1 FROM `device_stream` WHERE `device_id` = 'CAM_GATE_02' AND `stream_type` = 'sub');

INSERT INTO `device_stream` (`device_id`, `stream_type`, `channel_id`, `stream_url`, `stream_name`, `status`, `sort_no`)
SELECT * FROM (
  SELECT 'CAM_PARK_03' AS device_id, 'sub' AS stream_type, '34020000001320000005' AS channel_id,
         'rtsp://127.0.0.1:8554/live/cam03_sub' AS stream_url, '停车场-子码流' AS stream_name, 'ON' AS status, 1 AS sort_no
) t WHERE NOT EXISTS (SELECT 1 FROM `device_stream` WHERE `device_id` = 'CAM_PARK_03' AND `stream_type` = 'sub');

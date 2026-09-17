-- =============================================================================
-- 重建业务表（保留 sys_user 不动）
-- 包含：device_folder / device / device_stream + 初始化演示数据
--
-- 设备 status：INT  0=不可用 1=已启用 2=已停用
-- 时间字段：BIGINT 毫秒时间戳
--
-- 用法（Navicat）：选中 video_mid 库，整段执行
-- 警告：会清空设备、目录、码流全部数据！用户表不受影响。
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `video_mid`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `video_mid`;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS `device_stream`;
DROP TABLE IF EXISTS `device`;
DROP TABLE IF EXISTS `device_folder`;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE `device_folder` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `parent_id`     BIGINT       DEFAULT NULL COMMENT '父目录 ID，根为 NULL',
  `name`          VARCHAR(128) NOT NULL COMMENT '目录名称',
  `sort_no`       INT          NOT NULL DEFAULT 0 COMMENT '同级排序',
  `path`          VARCHAR(512) DEFAULT NULL COMMENT '物化路径，如 /1/2/',
  `created_at`    BIGINT       NOT NULL COMMENT '创建时间（毫秒时间戳）',
  `updated_at`    BIGINT       NOT NULL COMMENT '更新时间（毫秒时间戳）',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_path` (`path`(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备目录';

CREATE TABLE `device` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `device_id`     VARCHAR(64)  NOT NULL COMMENT '业务设备编码，全局唯一',
  `name`          VARCHAR(256) DEFAULT NULL COMMENT '设备名称',
  `platform_id`   VARCHAR(64)  DEFAULT NULL COMMENT '下级平台/国标 ID',
  `folder_id`     BIGINT       DEFAULT NULL COMMENT '所属目录 device_folder.id',
  `status`        INT          NOT NULL DEFAULT 2 COMMENT '0不可用 1已启用 2已停用',
  `manufacturer`  VARCHAR(128) DEFAULT NULL COMMENT '厂商',
  `model`         VARCHAR(128) DEFAULT NULL COMMENT '型号',
  `address`       VARCHAR(256) DEFAULT NULL COMMENT '安装地址',
  `ptz_type`      INT          NOT NULL DEFAULT 0 COMMENT '云台类型：0 无',
  `gateway_id`    VARCHAR(64)  DEFAULT NULL COMMENT '边缘网关 ID',
  `longitude`     DOUBLE       DEFAULT NULL COMMENT '经度',
  `latitude`      DOUBLE       DEFAULT NULL COMMENT '纬度',
  `created_at`    BIGINT       NOT NULL COMMENT '创建时间（毫秒时间戳）',
  `updated_at`    BIGINT       NOT NULL COMMENT '更新时间（毫秒时间戳）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_id` (`device_id`),
  KEY `idx_platform_id` (`platform_id`),
  KEY `idx_folder_id` (`folder_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频设备';

CREATE TABLE `device_stream` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `device_id`     VARCHAR(64)  NOT NULL COMMENT '关联 device.device_id',
  `stream_type`   VARCHAR(16)  NOT NULL COMMENT '码流类型：main / sub',
  `channel_id`    VARCHAR(64)  DEFAULT NULL COMMENT '国标通道编码（预留）',
  `stream_url`    VARCHAR(512) DEFAULT NULL COMMENT '播放/取流地址',
  `stream_name`   VARCHAR(256) DEFAULT NULL COMMENT '码流名称',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'OFF' COMMENT 'ON / OFF',
  `sort_no`       INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `live_enabled`  TINYINT      NOT NULL DEFAULT 0 COMMENT '业务端直播标记 0/1，同设备仅一条为 1',
  `created_at`    BIGINT       NOT NULL COMMENT '创建时间（毫秒时间戳）',
  `updated_at`    BIGINT       NOT NULL COMMENT '更新时间（毫秒时间戳）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_stream` (`device_id`, `stream_type`),
  UNIQUE KEY `uk_channel_id` (`channel_id`),
  KEY `idx_device_id` (`device_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备码流';

SET @now_ms = UNIX_TIMESTAMP(NOW(3)) * 1000;

INSERT INTO `device_folder` (`id`, `parent_id`, `name`, `sort_no`, `path`, `created_at`, `updated_at`) VALUES
(1, NULL, '园区',     1, '/1/', @now_ms, @now_ms),
(2, 1,    '东门区域', 1, '/1/2/', @now_ms, @now_ms),
(3, 1,    '地下车库', 2, '/1/3/', @now_ms, @now_ms);

ALTER TABLE `device_folder` AUTO_INCREMENT = 10;

INSERT INTO `device`
  (`device_id`, `name`, `platform_id`, `folder_id`, `status`, `manufacturer`, `model`, `address`, `ptz_type`, `gateway_id`, `longitude`, `latitude`, `created_at`, `updated_at`)
VALUES
('CAM_EAST_01', '东门球机',   '34020000002000000001', 2, 1, '宇视', 'IPC-B系列', '小区东门',     1, 'GW_COMMUNITY_01', 116.40, 39.90, @now_ms, @now_ms),
('CAM_GATE_02', '岗卡枪机',   '34020000002000000001', 2, 1, '宇视', 'IPC-B系列', '小区岗卡',     0, 'GW_COMMUNITY_01', 116.41, 39.91, @now_ms, @now_ms),
('CAM_PARK_03', '停车场半球', '34020000002000000001', 3, 2, '海康', 'DS-2CD',    '地下车库入口', 0, NULL,              116.39, 39.89, @now_ms, @now_ms);

INSERT INTO `device_stream`
  (`device_id`, `stream_type`, `channel_id`, `stream_url`, `stream_name`, `status`, `sort_no`, `live_enabled`, `created_at`, `updated_at`)
VALUES
('CAM_EAST_01', 'main', '34020000001320000001',
 'http://8.130.74.232:8080/live/cam01_main.live.flv', '东门-主码流', 'ON', 1, 0, @now_ms, @now_ms),
('CAM_EAST_01', 'sub',  '34020000001320000002',
 'http://8.130.74.232:8080/live/cam01_sub.live.flv',  '东门-子码流', 'ON', 2, 1, @now_ms, @now_ms),
('CAM_GATE_02', 'main', '34020000001320000003',
 'http://8.130.74.232:8080/live/cam02_main.live.flv', '岗卡-主码流', 'ON', 1, 0, @now_ms, @now_ms),
('CAM_GATE_02', 'sub',  '34020000001320000004',
 'http://8.130.74.232:8080/live/cam02_sub.live.flv',  '岗卡-子码流', 'ON', 2, 1, @now_ms, @now_ms),
('CAM_PARK_03', 'sub',  '34020000001320000005',
 'http://8.130.74.232:8080/live/cam03_sub.live.flv',  '停车场-子码流', 'ON', 1, 1, @now_ms, @now_ms);

SELECT 'sys_user（未改动）' AS item, COUNT(*) AS cnt FROM `sys_user`
UNION ALL SELECT 'device_folder', COUNT(*) FROM `device_folder`
UNION ALL SELECT 'device', COUNT(*) FROM `device`
UNION ALL SELECT 'device_stream', COUNT(*) FROM `device_stream`;

SELECT d.device_id, d.name, d.status, f.name AS folder_name, s.stream_type, s.live_enabled
FROM `device` d
LEFT JOIN `device_folder` f ON d.folder_id = f.id
LEFT JOIN `device_stream` s ON s.device_id = d.device_id
ORDER BY d.device_id, s.sort_no;

-- =============================================================================
-- video-mid 唯一数据库脚本：建库 + 建表 + 初始化数据
--
-- 表：sys_user / device_folder / device / device_stream / device_ptz_preset
-- 设备 status：INT  0=不可用 1=已启用 2=已停用
-- 时间字段：BIGINT 毫秒时间戳
--
-- sys_user 不写账号，启动 Java 后自动生成 admin / admin123
--
-- 用法：Navicat 选中后整段执行，然后重启 video-mid。
-- 警告：会 DROP 下列业务表，清空全部数据！
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `video_mid`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `video_mid`;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS `device_ptz_preset`;
DROP TABLE IF EXISTS `device_stream`;
DROP TABLE IF EXISTS `device`;
DROP TABLE IF EXISTS `device_folder`;
DROP TABLE IF EXISTS `sys_user`;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE `sys_user` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`      VARCHAR(64)  NOT NULL COMMENT '登录名',
  `password`      VARCHAR(128) NOT NULL COMMENT 'BCrypt 密码',
  `nickname`      VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
  `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1=正常',
  `last_login_at` BIGINT       DEFAULT NULL COMMENT '最后登录（毫秒时间戳）',
  `created_at`    BIGINT       NOT NULL COMMENT '创建时间（毫秒时间戳）',
  `updated_at`    BIGINT       NOT NULL COMMENT '更新时间（毫秒时间戳）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户';

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
  `stream_type`   VARCHAR(16)  NOT NULL COMMENT '码流类型：main/sub 或 visible_main 等',
  `channel_id`    VARCHAR(64)  DEFAULT NULL COMMENT '国标通道编码',
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

CREATE TABLE `device_ptz_preset` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `device_id`     VARCHAR(64)  NOT NULL COMMENT '关联 device.device_id',
  `preset_index`  INT          NOT NULL COMMENT '预置位编号',
  `name`          VARCHAR(128) NOT NULL COMMENT '业务名称',
  `zoom`          DOUBLE       DEFAULT NULL COMMENT '记录变倍，姿态以设备为准',
  `created_at`    BIGINT       NOT NULL COMMENT '创建时间（毫秒时间戳）',
  `updated_at`    BIGINT       NOT NULL COMMENT '更新时间（毫秒时间戳）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_preset` (`device_id`, `preset_index`),
  KEY `idx_device_id` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='云台预置位目录（姿态在设备）';

SET @now_ms = UNIX_TIMESTAMP(NOW(3)) * 1000;

-- ---------------------------------------------------------------------------
-- 业务演示数据
-- ---------------------------------------------------------------------------
INSERT INTO `device_folder` (`id`, `parent_id`, `name`, `sort_no`, `path`, `created_at`, `updated_at`) VALUES
(1, NULL, '园区',     1, '/1/',   @now_ms, @now_ms),
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
('CAM_EAST_01', 'main', '34020000001320000021',
 'http://8.130.74.232:8080/live/cam01_main.live.flv', '东门-主码流', 'ON', 1, 0, @now_ms, @now_ms),
('CAM_EAST_01', 'sub',  '34020000001320000022',
 'http://8.130.74.232:8080/live/cam01_sub.live.flv',  '东门-子码流', 'ON', 2, 1, @now_ms, @now_ms),
('CAM_GATE_02', 'main', '34020000001320000023',
 'http://8.130.74.232:8080/live/cam02_main.live.flv', '岗卡-主码流', 'ON', 1, 0, @now_ms, @now_ms),
('CAM_GATE_02', 'sub',  '34020000001320000024',
 'http://8.130.74.232:8080/live/cam02_sub.live.flv',  '岗卡-子码流', 'ON', 2, 1, @now_ms, @now_ms),
('CAM_PARK_03', 'sub',  '34020000001320000025',
 'http://8.130.74.232:8080/live/cam03_sub.live.flv',  '停车场-子码流', 'ON', 1, 1, @now_ms, @now_ms);

-- ---------------------------------------------------------------------------
-- 【仅 mock】开发期模拟云台，上线可删本段及 TIC7632_* 行
-- 与 resources/mock/uniview-devices.json 一致；通道号避开上面 CAM_* 的 021–025
-- ---------------------------------------------------------------------------
INSERT INTO `device`
  (`device_id`, `name`, `platform_id`, `folder_id`, `status`, `manufacturer`, `model`, `address`, `ptz_type`, `gateway_id`, `longitude`, `latitude`, `created_at`, `updated_at`)
VALUES
('TIC7632_01', '观测云台-东门（模拟）', '34020000002000000001', NULL, 1, '宇视', 'TIC7632-IRL@L-F75-4X56-GB-VH1', '小区东门制高点', 1, NULL, 116.40, 39.90, @now_ms, @now_ms),
('TIC7632_02', '观测云台-西区（模拟）', '34020000002000000001', NULL, 1, '宇视', 'TIC7632-IRL@L-F75-4X56-GB-VH1', '西区瞭望塔',     1, NULL, 116.39, 39.91, @now_ms, @now_ms);

INSERT INTO `device_stream`
  (`device_id`, `stream_type`, `channel_id`, `stream_url`, `stream_name`, `status`, `sort_no`, `live_enabled`, `created_at`, `updated_at`)
VALUES
('TIC7632_01', 'visible_main', '34020000001320000001',
 'http://8.130.74.232:8080/live/tic7632_01_visible_main.live.flv', '可见光-主码流', 'ON', 1, 0, @now_ms, @now_ms),
('TIC7632_01', 'visible_sub',  '34020000001320000002',
 'http://8.130.74.232:8080/live/tic7632_01_visible_sub.live.flv',  '可见光-子码流', 'ON', 2, 1, @now_ms, @now_ms),
('TIC7632_01', 'thermal_main', '34020000001320000003',
 'http://8.130.74.232:8080/live/tic7632_01_thermal_main.live.flv', '热成像-主码流', 'ON', 3, 0, @now_ms, @now_ms),
('TIC7632_02', 'visible_main', '34020000001320000011',
 'http://8.130.74.232:8080/live/tic7632_02_visible_main.live.flv', '可见光-主码流', 'ON', 1, 0, @now_ms, @now_ms),
('TIC7632_02', 'thermal_main', '34020000001320000012',
 'http://8.130.74.232:8080/live/tic7632_02_thermal_main.live.flv', '热成像-主码流', 'ON', 2, 1, @now_ms, @now_ms);

INSERT INTO `device_ptz_preset` (`device_id`, `preset_index`, `name`, `zoom`, `created_at`, `updated_at`) VALUES
('TIC7632_01', 1, '东门全景',   1.0,  @now_ms, @now_ms),
('TIC7632_01', 2, '岗卡特写',   8.0,  @now_ms, @now_ms),
('TIC7632_01', 3, '热成像周界', 1.0,  @now_ms, @now_ms),
('TIC7632_02', 1, '西区全景',   1.0,  @now_ms, @now_ms),
('TIC7632_02', 2, '停车场入口', 12.0, @now_ms, @now_ms);

SELECT 'sys_user（空，等启动写入 admin）' AS tbl, COUNT(*) AS cnt FROM `sys_user`
UNION ALL SELECT 'device_folder', COUNT(*) FROM `device_folder`
UNION ALL SELECT 'device', COUNT(*) FROM `device`
UNION ALL SELECT 'device_stream', COUNT(*) FROM `device_stream`
UNION ALL SELECT 'device_ptz_preset', COUNT(*) FROM `device_ptz_preset`;

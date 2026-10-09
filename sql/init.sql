-- =============================================================================
-- video-mid 唯一数据库脚本：建库 + 建表 + 初始化数据
--
-- 表：sys_user / device_folder / record_device / device / uniview_device / device_stream / device_ptz_preset
-- device.vendor：MOCK / UNIVIEW / HIKVISION，一台设备只对接一个平台
-- 设备 status：INT  0=不可用 1=已启用 2=已停用
-- 时间字段：BIGINT 毫秒时间戳
--
-- 初始化数据是当前库快照，已按 vendor / uniview_device 拆开
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
DROP TABLE IF EXISTS `uniview_device`;
DROP TABLE IF EXISTS `device`;
DROP TABLE IF EXISTS `record_device`;
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

CREATE TABLE `record_device` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`       VARCHAR(128) NOT NULL COMMENT '录像设备名称',
  `host`       VARCHAR(128) NOT NULL COMMENT 'NVR 地址',
  `port`       INT          NOT NULL COMMENT 'NVR 端口，登录协议为 ONVIF',
  `username`   VARCHAR(64)  NOT NULL COMMENT 'ONVIF 用户名',
  `password`   VARCHAR(128) NOT NULL COMMENT 'ONVIF 密码',
  `created_at` BIGINT       NOT NULL COMMENT '创建时间（毫秒时间戳）',
  `updated_at` BIGINT       NOT NULL COMMENT '更新时间（毫秒时间戳）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_record_device_login` (`host`, `port`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='宇视录像设备';

CREATE TABLE `device` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `device_id`     VARCHAR(64)  NOT NULL COMMENT '业务设备编码，全局唯一',
  `device_no`     INT          DEFAULT NULL COMMENT '位置编号，如101表示一层第一个摄像头（展示可格式化为0101）',
  `device_type`   INT          NOT NULL DEFAULT 1 COMMENT '设备类型：0抓拍 1视频流 2两者',
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
  `vendor`        VARCHAR(16)  NOT NULL DEFAULT 'MOCK' COMMENT 'MOCK / UNIVIEW / HIKVISION，一台设备只对接一个平台',
  `created_at`    BIGINT       NOT NULL COMMENT '创建时间（毫秒时间戳）',
  `updated_at`    BIGINT       NOT NULL COMMENT '更新时间（毫秒时间戳）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_id` (`device_id`),
  KEY `idx_platform_id` (`platform_id`),
  KEY `idx_folder_id` (`folder_id`),
  KEY `idx_status` (`status`),
  KEY `idx_vendor` (`vendor`),
  KEY `idx_device_no` (`device_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频设备';

CREATE TABLE `uniview_device` (
  `device_pk`     BIGINT       NOT NULL COMMENT 'device.id',
  `host`          VARCHAR(128) DEFAULT NULL COMMENT '宇视设备 IP',
  `port`          INT          DEFAULT NULL COMMENT '宇视设备端口',
  `username`      VARCHAR(64)  DEFAULT NULL COMMENT '宇视登录用户名',
  `password`      VARCHAR(128) DEFAULT NULL COMMENT '宇视登录密码',
  `access_channel` VARCHAR(32) DEFAULT NULL COMMENT 'LAPI 通道号，IPC 一般为 0',
  `access_status` VARCHAR(32)  NOT NULL DEFAULT 'unknown' COMMENT 'unknown/online/offline/auth_failed',
  `access_error`  VARCHAR(512) DEFAULT NULL COMMENT '最近一次登录或拉流失败原因',
  `lan_ip`        VARCHAR(64)  DEFAULT NULL COMMENT '摄像机内网 IP，用于在录像设备上匹配通道',
  `record_device_id` BIGINT    DEFAULT NULL COMMENT '宇视录像设备 record_device.id',
  `record_channel` INT         DEFAULT NULL COMMENT '录像设备上的通道号，由内网 IP 匹配得到',
  `record_channel_name` VARCHAR(128) DEFAULT NULL COMMENT '录像通道名称',
  PRIMARY KEY (`device_pk`),
  UNIQUE KEY `uk_uniview_login` (`host`, `port`, `access_channel`),
  UNIQUE KEY `uk_uniview_record_channel` (`record_device_id`, `record_channel`),
  KEY `idx_uniview_lan_ip` (`lan_ip`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='宇视设备登录与录像通道';

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
  `stream_index`  INT          DEFAULT NULL COMMENT 'LAPI 码流序号，主码流 0，子码流 1',
  `zlm_app`       VARCHAR(64)  DEFAULT NULL COMMENT 'ZLM app，宇视拉流为 live',
  `zlm_stream`    VARCHAR(128) DEFAULT NULL COMMENT 'ZLM stream，宇视拉流标识',
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

-- ---------------------------------------------------------------------------
-- 当前库快照。device 只保留共用字段，宇视登录和录像通道在 uniview_device。
-- ---------------------------------------------------------------------------
INSERT INTO `device_folder` (`id`, `parent_id`, `name`, `sort_no`, `path`, `created_at`, `updated_at`) VALUES
(1, NULL, '园区',     1, '/1/',   1790232886868, 1790232886868),
(2, 1,    '东门区域', 1, '/1/2/', 1790232886868, 1790232886868),
(3, 1,    '地下车库', 2, '/1/3/', 1790232886868, 1790232886868);
ALTER TABLE `device_folder` AUTO_INCREMENT = 4;

INSERT INTO `record_device` (`id`, `name`, `host`, `port`, `username`, `password`, `created_at`, `updated_at`) VALUES
(1, '智能网络视频录像机(1)', '39.185.236.176', 10052, 'guest', '*Guest321', 1790234265896, 1790234265896),
(2, '智能网络视频录像机(2)', '39.185.236.176', 10054, 'guest', '*Guest321', 1790234315841, 1790234315841);
ALTER TABLE `record_device` AUTO_INCREMENT = 3;

INSERT INTO `device`
  (`id`, `device_id`, `device_no`, `device_type`, `name`, `platform_id`, `folder_id`, `status`, `manufacturer`, `model`, `address`,
   `ptz_type`, `gateway_id`, `longitude`, `latitude`, `vendor`, `created_at`, `updated_at`)
VALUES
(1, 'UV_10135', 101, 2, '演示球机', '', 2, 1, '宇视', 'IPC-S6424-IR@P-X25-VF', '宇视在线调试',
 0, '', NULL, NULL, 'UNIVIEW', 1790232886868, 1790471416722);
ALTER TABLE `device` AUTO_INCREMENT = 2;

INSERT INTO `uniview_device`
  (`device_pk`, `host`, `port`, `username`, `password`, `access_channel`, `access_status`, `access_error`,
   `lan_ip`, `record_device_id`, `record_channel`, `record_channel_name`)
VALUES
(1, '39.185.236.176', 10135, 'guest', '*Guest321', '0', 'online', NULL,
 '192.168.1.160', 1, 1, '摄像机 01');

INSERT INTO `device_stream`
  (`id`, `device_id`, `stream_type`, `channel_id`, `stream_url`, `stream_name`, `status`, `sort_no`, `live_enabled`,
   `stream_index`, `zlm_app`, `zlm_stream`, `created_at`, `updated_at`)
VALUES
(1, 'UV_10135', 'main',  NULL, 'http://8.130.74.232:8080/live/uv_UV_10135_main.live.flv',  '主码流', 'OFF', 1, 0, 0, 'live', 'uv_UV_10135_main',  1790232886868, 1790236012327),
(2, 'UV_10135', 'sub',   NULL, 'http://8.130.74.232:8080/live/uv_UV_10135_sub.live.flv',   '辅码流', 'OFF', 2, 1, 1, 'live', 'uv_UV_10135_sub',   1790232886868, 1790472400039),
(3, 'UV_10135', 'third', NULL, 'http://8.130.74.232:8080/live/uv_UV_10135_third.live.flv', '第三流', 'OFF', 3, 0, 2, 'live', 'uv_UV_10135_third', 1790236012335, 1790236012335);
ALTER TABLE `device_stream` AUTO_INCREMENT = 4;

SELECT 'sys_user（空，等启动写入 admin）' AS tbl, COUNT(*) AS cnt FROM `sys_user`
UNION ALL SELECT 'device_folder', COUNT(*) FROM `device_folder`
UNION ALL SELECT 'record_device', COUNT(*) FROM `record_device`
UNION ALL SELECT 'device', COUNT(*) FROM `device`
UNION ALL SELECT 'uniview_device', COUNT(*) FROM `uniview_device`
UNION ALL SELECT 'device_stream', COUNT(*) FROM `device_stream`
UNION ALL SELECT 'device_ptz_preset', COUNT(*) FROM `device_ptz_preset`;

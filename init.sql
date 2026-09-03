-- 极知AI 视频中台 video-mid 数据库初始化脚本
-- 库名: video_mid  用户: root  密码: 123456

CREATE DATABASE IF NOT EXISTS `video_mid`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `video_mid`;

-- 设备表：存国标 Catalog 解析后的设备/通道业务元数据（主/子码流）
-- 禁止用本地文件或 Redis 持久化设备业务数据
CREATE TABLE IF NOT EXISTS `device` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `device_id`       VARCHAR(64)  NOT NULL COMMENT '国标设备/通道编码',
  `parent_id`       VARCHAR(64)  DEFAULT NULL COMMENT '父节点国标编码（目录树）',
  `name`            VARCHAR(256) DEFAULT NULL COMMENT '设备/通道名称',
  `manufacturer`    VARCHAR(128) DEFAULT NULL COMMENT '厂家',
  `model`           VARCHAR(128) DEFAULT NULL COMMENT '型号',
  `owner`           VARCHAR(128) DEFAULT NULL COMMENT '归属',
  `civil_code`      VARCHAR(64)  DEFAULT NULL COMMENT '行政区域',
  `address`         VARCHAR(256) DEFAULT NULL COMMENT '安装地址',
  `parental`        INT          DEFAULT 0 COMMENT '是否有子设备 1有 0无',
  `register_way`    INT          DEFAULT NULL COMMENT '注册方式',
  `secrecy`         INT          DEFAULT 0 COMMENT '保密属性',
  `status`          VARCHAR(16)  DEFAULT 'OFF' COMMENT '在线状态 ON/OFF（Catalog/心跳更新）',
  `device_type`     VARCHAR(32)  DEFAULT 'CAMERA' COMMENT '节点类型: PLATFORM/REGION/CAMERA',
  `stream_type`     VARCHAR(16)  DEFAULT NULL COMMENT '码流类型: main/sub，通道级有效',
  `main_channel_id` VARCHAR(64)  DEFAULT NULL COMMENT '主码流通道国标 ID',
  `sub_channel_id`  VARCHAR(64)  DEFAULT NULL COMMENT '子码流通道国标 ID',
  `ptz_type`        INT          DEFAULT 0 COMMENT '云台类型 0无 1球机等',
  `gateway_id`      VARCHAR(64)  DEFAULT NULL COMMENT '所属边缘网关 ID（云台用）',
  `platform_id`     VARCHAR(64)  DEFAULT NULL COMMENT '下级平台国标 ID（宇视）',
  `longitude`       DOUBLE       DEFAULT NULL COMMENT '经度',
  `latitude`        DOUBLE       DEFAULT NULL COMMENT '纬度',
  `extra_xml`       TEXT         DEFAULT NULL COMMENT 'Catalog 原始扩展字段备份',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_id` (`device_id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_platform_id` (`platform_id`),
  KEY `idx_gateway_id` (`gateway_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='国标设备/通道表';

-- 下级平台注册信息（业务元数据，持久化 MySQL）
CREATE TABLE IF NOT EXISTS `sip_platform` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `platform_id`     VARCHAR(64)  NOT NULL COMMENT '下级平台国标编码',
  `domain`          VARCHAR(64)  DEFAULT NULL COMMENT 'SIP 域',
  `ip`              VARCHAR(64)  DEFAULT NULL COMMENT '注册来源 IP',
  `port`            INT          DEFAULT NULL COMMENT '注册来源端口',
  `transport`       VARCHAR(8)   DEFAULT 'UDP' COMMENT 'UDP/TCP',
  `status`          VARCHAR(16)  DEFAULT 'OFFLINE' COMMENT 'ONLINE/OFFLINE',
  `last_register_at` DATETIME    DEFAULT NULL,
  `last_keepalive_at` DATETIME   DEFAULT NULL,
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_id` (`platform_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='下级国标平台（宇视）注册表';

-- 点播会话审计（可选业务记录；运行时引用计数仍在 Redis）
CREATE TABLE IF NOT EXISTS `stream_session_log` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `session_id`      VARCHAR(64)  NOT NULL COMMENT '会话 UUID',
  `device_id`       VARCHAR(64)  NOT NULL COMMENT '点播通道 ID',
  `stream_type`     VARCHAR(16)  DEFAULT 'sub' COMMENT 'main/sub',
  `play_type`       VARCHAR(16)  NOT NULL COMMENT 'LIVE/PLAYBACK',
  `start_time`      DATETIME     DEFAULT NULL COMMENT '回放开始',
  `end_time`        DATETIME     DEFAULT NULL COMMENT '回放结束',
  `stream_id`       VARCHAR(128) DEFAULT NULL COMMENT 'ZLM streamId',
  `rtp_port`        INT          DEFAULT NULL,
  `play_url_flv`    VARCHAR(512) DEFAULT NULL,
  `play_url_hls`    VARCHAR(512) DEFAULT NULL,
  `status`          VARCHAR(16)  DEFAULT 'STARTING' COMMENT 'STARTING/PLAYING/STOPPED/FAILED',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `stopped_at`      DATETIME     DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_device_id` (`device_id`),
  KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='点播会话审计日志';

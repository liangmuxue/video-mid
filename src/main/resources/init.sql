-- video-mid 全库初始化（新库执行）
-- 业务表重建（保留用户）请用 sql/rebuild_business_tables.sql

CREATE DATABASE IF NOT EXISTS `video_mid`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `video_mid`;

CREATE TABLE IF NOT EXISTS `sys_user` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `username`      VARCHAR(64)  NOT NULL,
  `password`      VARCHAR(128) NOT NULL,
  `nickname`      VARCHAR(64)  DEFAULT NULL,
  `status`        TINYINT      NOT NULL DEFAULT 1,
  `last_login_at` BIGINT       DEFAULT NULL COMMENT '毫秒时间戳',
  `created_at`    BIGINT       NOT NULL COMMENT '毫秒时间戳',
  `updated_at`    BIGINT       NOT NULL COMMENT '毫秒时间戳',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户';

CREATE TABLE IF NOT EXISTS `device_folder` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `parent_id`     BIGINT       DEFAULT NULL,
  `name`          VARCHAR(128) NOT NULL,
  `sort_no`       INT          NOT NULL DEFAULT 0,
  `path`          VARCHAR(512) DEFAULT NULL,
  `created_at`    BIGINT       NOT NULL COMMENT '毫秒时间戳',
  `updated_at`    BIGINT       NOT NULL COMMENT '毫秒时间戳',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_path` (`path`(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备目录';

CREATE TABLE IF NOT EXISTS `device` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `device_id`     VARCHAR(64)  NOT NULL,
  `name`          VARCHAR(256) DEFAULT NULL,
  `platform_id`   VARCHAR(64)  DEFAULT NULL,
  `folder_id`     BIGINT       DEFAULT NULL,
  `status`        INT          NOT NULL DEFAULT 2 COMMENT '0不可用 1已启用 2已停用',
  `manufacturer`  VARCHAR(128) DEFAULT NULL,
  `model`         VARCHAR(128) DEFAULT NULL,
  `address`       VARCHAR(256) DEFAULT NULL,
  `ptz_type`      INT          NOT NULL DEFAULT 0,
  `gateway_id`    VARCHAR(64)  DEFAULT NULL,
  `longitude`     DOUBLE       DEFAULT NULL,
  `latitude`      DOUBLE       DEFAULT NULL,
  `created_at`    BIGINT       NOT NULL COMMENT '毫秒时间戳',
  `updated_at`    BIGINT       NOT NULL COMMENT '毫秒时间戳',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_id` (`device_id`),
  KEY `idx_platform_id` (`platform_id`),
  KEY `idx_folder_id` (`folder_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频设备';

CREATE TABLE IF NOT EXISTS `device_stream` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `device_id`     VARCHAR(64)  NOT NULL,
  `stream_type`   VARCHAR(16)  NOT NULL,
  `channel_id`    VARCHAR(64)  DEFAULT NULL,
  `stream_url`    VARCHAR(512) DEFAULT NULL,
  `stream_name`   VARCHAR(256) DEFAULT NULL,
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'OFF',
  `sort_no`       INT          NOT NULL DEFAULT 0,
  `live_enabled`  TINYINT      NOT NULL DEFAULT 0,
  `created_at`    BIGINT       NOT NULL COMMENT '毫秒时间戳',
  `updated_at`    BIGINT       NOT NULL COMMENT '毫秒时间戳',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_stream` (`device_id`, `stream_type`),
  UNIQUE KEY `uk_channel_id` (`channel_id`),
  KEY `idx_device_id` (`device_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备码流';

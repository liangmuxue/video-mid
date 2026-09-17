-- sys_user 时间字段改为毫秒时间戳（保留已有用户数据）
-- 在 video_mid 库执行；若已是 BIGINT 可跳过

USE `video_mid`;

ALTER TABLE `sys_user`
  MODIFY COLUMN `last_login_at` BIGINT DEFAULT NULL COMMENT '毫秒时间戳',
  MODIFY COLUMN `created_at` BIGINT NOT NULL COMMENT '毫秒时间戳',
  MODIFY COLUMN `updated_at` BIGINT NOT NULL COMMENT '毫秒时间戳';

-- 若原列为 DATETIME，先备份再转换（按需取消注释）：
-- UPDATE sys_user SET
--   created_at = UNIX_TIMESTAMP(created_at) * 1000,
--   updated_at = UNIX_TIMESTAMP(updated_at) * 1000,
--   last_login_at = IF(last_login_at IS NULL, NULL, UNIX_TIMESTAMP(last_login_at) * 1000);

-- 设备 status 迁移为 INT：0=不可用 1=已启用 2=已停用
-- 在已有 VARCHAR 状态库上执行（执行前请备份）

USE `video_mid`;

UPDATE `device` SET status = '1' WHERE status IN ('已启用', 'ON', 'on', 'ENABLED', '1');
UPDATE `device` SET status = '2' WHERE status IN ('已停用', 'OFF', 'off', 'DISABLED', '2');
UPDATE `device` SET status = '0' WHERE status IN ('不可用', 'UNAVAILABLE', 'unavailable', '0');

ALTER TABLE `device`
  MODIFY COLUMN `status` INT NOT NULL DEFAULT 2 COMMENT '0不可用 1已启用 2已停用';

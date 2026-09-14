-- 设备状态：已启用 / 已停用 / 不可用（兼容历史 ON/OFF）
UPDATE device SET status = '已启用' WHERE status IN ('ON', 'on', 'ENABLED');
UPDATE device SET status = '已停用' WHERE status IN ('OFF', 'off', 'DISABLED');
UPDATE device SET status = '不可用' WHERE status IN ('UNAVAILABLE', 'unavailable');

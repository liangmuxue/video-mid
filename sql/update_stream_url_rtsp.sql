-- 把演示码流地址更新为 RTSP（MediaMTX）
USE `video_mid`;

UPDATE `device_stream` SET `stream_url` = 'rtsp://127.0.0.1:8554/live/cam01_main'
WHERE `device_id` = 'CAM_EAST_01' AND `stream_type` = 'main';

UPDATE `device_stream` SET `stream_url` = 'rtsp://127.0.0.1:8554/live/cam01_sub'
WHERE `device_id` = 'CAM_EAST_01' AND `stream_type` = 'sub';

UPDATE `device_stream` SET `stream_url` = 'rtsp://127.0.0.1:8554/live/cam02_main'
WHERE `device_id` = 'CAM_GATE_02' AND `stream_type` = 'main';

UPDATE `device_stream` SET `stream_url` = 'rtsp://127.0.0.1:8554/live/cam02_sub'
WHERE `device_id` = 'CAM_GATE_02' AND `stream_type` = 'sub';

UPDATE `device_stream` SET `stream_url` = 'rtsp://127.0.0.1:8554/live/cam03_sub'
WHERE `device_id` = 'CAM_PARK_03' AND `stream_type` = 'sub';

package com.jizhi.videomid.ptz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jizhi.videomid.device.entity.DeviceEntity;
import com.jizhi.videomid.device.service.DeviceService;
import com.jizhi.videomid.session.RedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 云台服务：REST 收令立刻 accepted，写入 Redis 队列，经 WebSocket 下发网关。
 */
@Service
public class PtzService {

    private static final Logger log = LoggerFactory.getLogger(PtzService.class);
    private static final Set<String> ALLOWED_CMDS = Set.of(
            "left", "right", "up", "down", "zoomIn", "zoomOut", "stop"
    );

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final PtzWebSocketHandler webSocketHandler;
    private final DeviceService deviceService;

    public PtzService(StringRedisTemplate redis,
                      ObjectMapper objectMapper,
                      PtzWebSocketHandler webSocketHandler,
                      DeviceService deviceService) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.webSocketHandler = webSocketHandler;
        this.deviceService = deviceService;
    }

    public Map<String, Object> move(String deviceId, String cmd, Integer speed, Integer timeoutMs) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId required");
        }
        if (cmd == null || !ALLOWED_CMDS.contains(cmd)) {
            throw new IllegalArgumentException("cmd must be one of " + ALLOWED_CMDS);
        }
        if (speed != null && (speed < 1 || speed > 8)) {
            throw new IllegalArgumentException("speed must be 1-8");
        }

        long seq = redis.opsForValue().increment(RedisKeys.ptzSeq());
        if (seq == 0) {
            seq = 1;
        }
        PtzCommand command = new PtzCommand(seq, deviceId, cmd, speed, timeoutMs);

        try {
            String json = objectMapper.writeValueAsString(command);
            redis.opsForList().rightPush(RedisKeys.ptzQueue(deviceId), json);
            redis.expire(RedisKeys.ptzQueue(deviceId), Duration.ofHours(1));
        } catch (Exception e) {
            throw new IllegalStateException("queue ptz command failed", e);
        }

        String gatewayId = resolveGatewayId(deviceId);
        boolean delivered = false;
        if (gatewayId != null) {
            delivered = webSocketHandler.sendToGateway(gatewayId, command);
        } else {
            log.warn("No gateway mapped for device {}, command queued seq={}", deviceId, seq);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", "accepted");
        result.put("seq", seq);
        result.put("deviceId", deviceId);
        result.put("cmd", cmd);
        result.put("gatewayId", gatewayId);
        result.put("delivered", delivered);
        return result;
    }

    private String resolveGatewayId(String deviceId) {
        return deviceService.findByDeviceId(deviceId)
                .map(DeviceEntity::getGatewayId)
                .filter(g -> g != null && !g.isBlank())
                .orElse(null);
    }
}

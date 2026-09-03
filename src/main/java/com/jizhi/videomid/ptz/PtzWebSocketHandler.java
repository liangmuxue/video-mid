package com.jizhi.videomid.ptz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jizhi.videomid.session.RedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 云台 WebSocket 服务端：等待现场 edge-gateway 建立长连接。
 * 路径：ws://中台/ptz/gateway?id=小区编号
 */
@Component
public class PtzWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PtzWebSocketHandler.class);

    private final ConcurrentHashMap<String, WebSocketSession> gatewaySessions = new ConcurrentHashMap<>();
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public PtzWebSocketHandler(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String gatewayId = resolveGatewayId(session);
        if (gatewayId == null || gatewayId.isBlank()) {
            log.warn("Gateway connected without id, closing");
            try {
                session.close(CloseStatus.BAD_DATA);
            } catch (Exception ignored) {
            }
            return;
        }
        gatewaySessions.put(gatewayId, session);
        redis.opsForValue().set(RedisKeys.gwSession(gatewayId), session.getId(), Duration.ofHours(24));
        log.info("edge-gateway connected: id={} session={}", gatewayId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("Gateway message from {}: {}", resolveGatewayId(session), payload);
        // 网关 ACK / 心跳
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(payload, Map.class);
            String type = String.valueOf(map.getOrDefault("type", ""));
            if ("heartbeat".equalsIgnoreCase(type) || "ping".equalsIgnoreCase(type)) {
                session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
                String gatewayId = resolveGatewayId(session);
                if (gatewayId != null) {
                    redis.opsForValue().set(RedisKeys.gwSession(gatewayId), session.getId(), Duration.ofHours(24));
                }
            }
            // ack: {"type":"ack","seq":123}
        } catch (Exception e) {
            log.warn("Invalid gateway message: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String gatewayId = resolveGatewayId(session);
        if (gatewayId != null) {
            gatewaySessions.remove(gatewayId, session);
            redis.delete(RedisKeys.gwSession(gatewayId));
            log.info("edge-gateway disconnected: id={} status={}", gatewayId, status);
        }
    }

    public boolean sendToGateway(String gatewayId, PtzCommand command) {
        WebSocketSession session = gatewaySessions.get(gatewayId);
        if (session == null || !session.isOpen()) {
            log.warn("Gateway {} offline, command queued only seq={}", gatewayId, command.getSeq());
            return false;
        }
        try {
            String json = objectMapper.writeValueAsString(command);
            session.sendMessage(new TextMessage(json));
            return true;
        } catch (Exception e) {
            log.error("Send PTZ to gateway {} failed", gatewayId, e);
            return false;
        }
    }

    public boolean isGatewayOnline(String gatewayId) {
        WebSocketSession session = gatewaySessions.get(gatewayId);
        return session != null && session.isOpen();
    }

    private String resolveGatewayId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        for (String part : uri.getQuery().split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && "id".equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }
}

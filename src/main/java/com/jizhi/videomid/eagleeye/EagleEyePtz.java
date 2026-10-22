package com.jizhi.videomid.eagleeye;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** 统一云台入口里的鹰眼后台。方向先记住速度，再下发移动。 */
@Component
public class EagleEyePtz {

    private final EagleEyeControlClient client;

    public EagleEyePtz(EagleEyeControlClient client) {
        this.client = client;
    }

    public Map<String, Object> move(String deviceId, String direction, int speed) {
        if ("stop".equalsIgnoreCase(direction)) {
            client.send(deviceId, "ptz_stop", null);
            return done("move", deviceId, speed);
        }
        String protocolDirection = EagleEyeCommands.direction(direction);
        if (protocolDirection == null) {
            throw new IllegalArgumentException("不支持的云台方向: " + direction);
        }
        int level = EagleEyeCommands.speed(speed);
        client.send(deviceId, "ptz_speed", Map.of("speedLevel", level));
        client.send(deviceId, "ptz_direction", Map.of("direction", protocolDirection));
        return done("move", deviceId, level);
    }

    public Map<String, Object> zoom(String deviceId, String action, int speed) {
        String zoom = EagleEyeCommands.zoom(action);
        if (zoom == null) {
            throw new IllegalArgumentException("不支持的变倍动作: " + action);
        }
        int level = EagleEyeCommands.speed(speed);
        client.send(deviceId, "camera_zoom", Map.of("zoom", zoom));
        return done("zoom", deviceId, level);
    }

    public static Map<String, Object> unsupported() {
        throw new IllegalArgumentException("鹰眼子系统不支持该控制");
    }

    public Map<String, Object> focus(String deviceId, String action, int speed) {
        String focus = EagleEyeCommands.focus(action);
        if (focus == null) {
            throw new IllegalArgumentException("不支持的对焦动作: " + action);
        }
        int level = EagleEyeCommands.speed(speed);
        client.send(deviceId, "camera_focus", Map.of("focus", focus));
        return done("focus", deviceId, level);
    }

    private static Map<String, Object> done(String action, String deviceId, int speed) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("success", true);
        row.put("mock", false);
        row.put("live", true);
        row.put("action", action);
        row.put("deviceId", deviceId);
        row.put("speed", speed);
        row.put("response", "");
        return row;
    }
}

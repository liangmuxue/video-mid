package com.jizhi.videomid.uniview.cloud;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 宇视云云台。开始后必须用同一个命令调用停止，才能换方向。 */
@Service
public class UniviewCloudPtz {

    private final UniviewCloudClient client;
    private final ConcurrentHashMap<String, Integer> lastCommand = new ConcurrentHashMap<>();

    public UniviewCloudPtz(UniviewCloudClient client) {
        this.client = client;
    }

    public Map<String, Object> move(String deviceId, String direction, int speed) {
        int command = directionCommand(direction);
        if ("stop".equalsIgnoreCase(direction)) {
            return stop(deviceId, command, "move");
        }
        return start(deviceId, command, speed, "move");
    }

    public Map<String, Object> zoom(String deviceId, String action, int speed) {
        int command = zoomCommand(action);
        return pulse(deviceId, command, speed, "zoom");
    }

    public Map<String, Object> focus(String deviceId, String action, int speed) {
        int command = focusCommand(action);
        return pulse(deviceId, command, speed, "focus");
    }

    public Map<String, Object> wideAngle(String deviceId) {
        return pulse(deviceId, 9, 4, "wide-angle");
    }

    public Map<String, Object> gotoPreset(String deviceId, int presetIndex) {
        UniviewCloudDeviceIds.Target target = UniviewCloudDeviceIds.parse(deviceId);
        Map<String, Object> body = base(target);
        body.put("presetIndex", presetIndex);
        JsonNode data = client.post("/openapi/device/ptz/preset/goto", body);
        return ok("goto-preset", deviceId, data);
    }

    public Map<String, Object> setPreset(String deviceId, int presetIndex, String name) {
        UniviewCloudDeviceIds.Target target = UniviewCloudDeviceIds.parse(deviceId);
        Map<String, Object> body = base(target);
        body.put("presetIndex", presetIndex);
        body.put("name", name == null || name.isBlank() ? "预置位" + presetIndex : name.trim());
        JsonNode data;
        try {
            data = client.post("/openapi/device/ptz/preset/add", body);
        } catch (IllegalArgumentException addFailed) {
            data = client.post("/openapi/device/ptz/preset/update", body);
        }
        return ok("set-preset", deviceId, data);
    }

    public Map<String, Object> snapshot(String deviceId, String channelType) {
        UniviewCloudDeviceIds.Target target = UniviewCloudDeviceIds.parse(deviceId);
        JsonNode data = client.post("/openapi/device/capture/get", base(target));
        Map<String, Object> row = ok("snapshot", deviceId, data);
        row.put("channelType", channelType == null || channelType.isBlank() ? "visible" : channelType);
        String url = data.path("url").asText("");
        if (!url.isBlank()) {
            row.put("url", url);
        }
        return row;
    }

    public List<Map<String, Object>> listPresets(String deviceId) {
        UniviewCloudDeviceIds.Target target = UniviewCloudDeviceIds.parse(deviceId);
        List<Map<String, Object>> presets = new ArrayList<>();
        int start = 0;
        while (start < 1_000) {
            Map<String, Object> body = base(target);
            body.put("pageStart", start);
            body.put("pageSize", 100);
            JsonNode data = client.post("/openapi/device/ptz/preset/list", body);
            JsonNode list = data.path("presetList");
            int count = list.isArray() ? list.size() : 0;
            if (count == 0) {
                break;
            }
            for (JsonNode item : list) {
                Map<String, Object> preset = new LinkedHashMap<>();
                preset.put("index", item.path("presetIndex").asInt());
                preset.put("name", item.path("name").asText(""));
                presets.add(preset);
            }
            int total = data.path("total").asInt(presets.size());
            start += count;
            if (start >= total || count < 100) {
                break;
            }
        }
        return presets;
    }

    private Map<String, Object> pulse(String deviceId, int command, int speed, String action) {
        Map<String, Object> started = start(deviceId, command, speed, action);
        try {
            Thread.sleep(400);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        stop(deviceId, command, action + "-stop");
        return started;
    }

    private Map<String, Object> start(String deviceId, int command, int speed, String action) {
        UniviewCloudDeviceIds.Target target = UniviewCloudDeviceIds.parse(deviceId);
        Map<String, Object> body = base(target);
        body.put("command", command);
        body.put("speed", clampSpeed(speed));
        JsonNode data = client.post("/openapi/device/ptz/start", body);
        lastCommand.put(deviceId, command);
        Map<String, Object> row = ok(action, deviceId, data);
        row.put("command", command);
        return row;
    }

    private Map<String, Object> stop(String deviceId, int command, String action) {
        UniviewCloudDeviceIds.Target target = UniviewCloudDeviceIds.parse(deviceId);
        Integer remembered = lastCommand.get(deviceId);
        int used = remembered == null ? command : remembered;
        Map<String, Object> body = base(target);
        body.put("command", used);
        JsonNode data = client.post("/openapi/device/ptz/stop", body);
        lastCommand.remove(deviceId);
        return ok(action, deviceId, data);
    }

    private static Map<String, Object> base(UniviewCloudDeviceIds.Target target) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceSerial", target.serial());
        body.put("channelNo", target.channelNo());
        return body;
    }

    private static Map<String, Object> ok(String action, String deviceId, JsonNode data) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("success", true);
        row.put("live", true);
        row.put("action", action);
        row.put("deviceId", deviceId);
        row.put("response", data == null ? "" : data.toString());
        return row;
    }

    private static int clampSpeed(int speed) {
        return Math.max(1, Math.min(speed, 9));
    }

    private static int directionCommand(String direction) {
        return switch (direction == null ? "" : direction.toLowerCase()) {
            case "up" -> 0;
            case "down" -> 1;
            case "left" -> 2;
            case "right" -> 3;
            case "left_up" -> 4;
            case "left_down" -> 5;
            case "right_up" -> 6;
            case "right_down" -> 7;
            case "stop" -> 0;
            default -> throw new IllegalArgumentException("不支持的云台方向: " + direction);
        };
    }

    private static int zoomCommand(String action) {
        return switch (action == null ? "" : action.toLowerCase()) {
            case "in", "zoom_in", "tele" -> 8;
            case "out", "zoom_out", "wide" -> 9;
            default -> throw new IllegalArgumentException("不支持的变倍动作: " + action);
        };
    }

    private static int focusCommand(String action) {
        return switch (action == null ? "" : action.toLowerCase()) {
            case "near", "focus_near" -> 10;
            case "far", "focus_far" -> 11;
            default -> throw new IllegalArgumentException("不支持的对焦动作: " + action);
        };
    }
}

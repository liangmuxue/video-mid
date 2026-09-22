package com.jizhi.videomid.uniview.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.jizhi.videomid.uniview.UniviewPtzPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 宇视 LAPI PTZ 控制 */
@Service
@ConditionalOnProperty(name = "uniview.data-source", havingValue = "live")
public class LiveUniviewPtzAdapter implements UniviewPtzPort {

    private final UniviewLapiClient lapiClient;

    public LiveUniviewPtzAdapter(UniviewLapiClient lapiClient) {
        this.lapiClient = lapiClient;
    }

    @Override
    public List<Map<String, Object>> listPtzDevices() {
        return lapiClient.parseDevicesFromChannels(
                lapiClient.get("/LAPI/V1.0/Channels/System/ChannelDetailInfos"));
    }

    @Override
    public Map<String, Object> move(String deviceId, String direction, int speed) {
        return ptzContinuous(deviceId, directionMap(direction), speed, "move");
    }

    @Override
    public Map<String, Object> zoom(String deviceId, String action, int speed) {
        String dir = "zoom_in".equalsIgnoreCase(action) ? "ZoomTele" : "ZoomWide";
        return ptzContinuous(deviceId, dir, speed, "zoom");
    }

    @Override
    public Map<String, Object> focus(String deviceId, String action, int speed) {
        String dir = "focus_near".equalsIgnoreCase(action) ? "FocusNear" : "FocusFar";
        return ptzContinuous(deviceId, dir, speed, "focus");
    }

    @Override
    public Map<String, Object> wideAngle(String deviceId) {
        return ptzContinuous(deviceId, "ZoomWide", 4, "wide-angle");
    }

    @Override
    public Map<String, Object> gotoPreset(String deviceId, int presetIndex) {
        lapiClient.ensureConfigured();
        String channelId = resolveChannelId(deviceId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("PresetID", presetIndex);
        JsonNode resp = lapiClient.put(
                "/LAPI/V1.0/Channels/" + channelId + "/PTZ/Preset/" + presetIndex + "/Goto", body);
        return ok("goto-preset", Map.of(
                "deviceId", deviceId,
                "presetIndex", presetIndex,
                "response", resp.toString()
        ));
    }

    @Override
    public Map<String, Object> setPreset(String deviceId, int presetIndex, String name, boolean overwrite) {
        lapiClient.ensureConfigured();
        String channelId = resolveChannelId(deviceId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("PresetID", presetIndex);
        body.put("PresetName", name);
        body.put("Overwrite", overwrite);
        JsonNode resp = lapiClient.put(
                "/LAPI/V1.0/Channels/" + channelId + "/PTZ/Preset/" + presetIndex, body);
        return ok("set-preset", Map.of(
                "deviceId", deviceId,
                "presetIndex", presetIndex,
                "name", name,
                "overwrite", overwrite,
                "response", resp.toString()
        ));
    }

    @Override
    public Map<String, Object> snapshot(String deviceId, String channelType) {
        lapiClient.ensureConfigured();
        String channelId = resolveChannelId(deviceId);
        JsonNode resp = lapiClient.get("/LAPI/V1.0/Channels/" + channelId + "/Media/Snapshot/1");
        return ok("snapshot", Map.of(
                "deviceId", deviceId,
                "channelType", channelType == null ? "visible" : channelType,
                "response", resp.toString()
        ));
    }

    private Map<String, Object> ptzContinuous(String deviceId, String direction, int speed, String action) {
        lapiClient.ensureConfigured();
        String channelId = resolveChannelId(deviceId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("PTZCmd", direction);
        body.put("Speed", Math.max(1, Math.min(speed, 8)));
        JsonNode resp = lapiClient.put(
                "/LAPI/V1.0/Channels/" + channelId + "/PTZ/ContinuousMove", body);
        return ok(action, Map.of(
                "deviceId", deviceId,
                "direction", direction,
                "speed", speed,
                "response", resp.toString()
        ));
    }

    private String resolveChannelId(String deviceId) {
        List<Map<String, Object>> devices = listPtzDevices();
        for (Map<String, Object> d : devices) {
            if (deviceId.equals(String.valueOf(d.get("deviceId")))) {
                Object chs = d.get("channels");
                if (chs instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> ch) {
                    Object id = ch.get("channelId");
                    if (id != null && !String.valueOf(id).isBlank()) {
                        return String.valueOf(id);
                    }
                }
            }
        }
        return "1";
    }

    private static String directionMap(String direction) {
        return switch (direction == null ? "" : direction.toLowerCase()) {
            case "up" -> "Up";
            case "down" -> "Down";
            case "left" -> "Left";
            case "right" -> "Right";
            case "left_up" -> "LeftUp";
            case "left_down" -> "LeftDown";
            case "right_up" -> "RightUp";
            case "right_down" -> "RightDown";
            default -> "Stop";
        };
    }

    private static Map<String, Object> ok(String action, Map<String, Object> data) {
        Map<String, Object> m = new HashMap<>(data);
        m.put("success", true);
        m.put("mock", false);
        m.put("live", true);
        m.put("action", action);
        return m;
    }
}

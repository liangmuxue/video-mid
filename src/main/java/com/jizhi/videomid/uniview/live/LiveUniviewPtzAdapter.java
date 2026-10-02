package com.jizhi.videomid.uniview.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.jizhi.videomid.device.Device;
import com.jizhi.videomid.device.DeviceRepository;
import com.jizhi.videomid.uniview.UniviewPtzPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 宇视 LAPI PTZ 控制。连续动作走 PTZCtrl 指令码。 */
@Service
public class LiveUniviewPtzAdapter implements UniviewPtzPort {

    private static final Logger log = LoggerFactory.getLogger(LiveUniviewPtzAdapter.class);
    private static final int CMD_STOP = 0x0901;
    private static final int CMD_ZOOM_IN = 0x0302;
    private static final int CMD_ZOOM_OUT = 0x0304;
    private static final int CMD_FOCUS_NEAR = 0x0202;
    private static final int CMD_FOCUS_FAR = 0x0204;
    /** 点按变倍/对焦的持续时间，随后发停止，避免镜头一直动 */
    private static final long CLICK_PULSE_MS = 400;

    private final UniviewLapiClient lapiClient;
    private final DeviceRepository deviceRepository;

    public LiveUniviewPtzAdapter(UniviewLapiClient lapiClient, DeviceRepository deviceRepository) {
        this.lapiClient = lapiClient;
        this.deviceRepository = deviceRepository;
    }

    @Override
    public List<Map<String, Object>> listPtzDevices() {
        List<Map<String, Object>> devices = new ArrayList<>();
        for (Device device : deviceRepository.findAll()) {
            if (device.getHost() == null || device.getHost().isBlank()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("deviceId", device.getDeviceId());
            item.put("name", device.getName());
            item.put("presets", loadPresets(device));
            devices.add(item);
        }
        return devices;
    }

    @Override
    public Map<String, Object> move(String deviceId, String direction, int speed) {
        return ptzCtrl(deviceId, directionCmd(direction), speed, "move");
    }

    @Override
    public Map<String, Object> zoom(String deviceId, String action, int speed) {
        return clickPulse(deviceId, zoomCmd(action), speed, "zoom");
    }

    @Override
    public Map<String, Object> focus(String deviceId, String action, int speed) {
        return clickPulse(deviceId, focusCmd(action), speed, "focus");
    }

    @Override
    public Map<String, Object> wideAngle(String deviceId) {
        return clickPulse(deviceId, CMD_ZOOM_OUT, 4, "wide-angle");
    }

    @Override
    public Map<String, Object> gotoPreset(String deviceId, int presetIndex) {
        Device device = requireDevice(deviceId);
        String channelId = channelOf(device);
        JsonNode resp = lapiClient.put(LapiEndpoint.from(device),
                "/LAPI/V1.0/Channels/" + channelId + "/PTZ/Presets/" + presetIndex + "/Goto",
                Map.of());
        ensureLapiOk(resp);
        return ok("goto-preset", Map.of(
                "deviceId", deviceId,
                "presetIndex", presetIndex,
                "response", resp.toString()
        ));
    }

    @Override
    public Map<String, Object> setPreset(String deviceId, int presetIndex, String name, boolean overwrite) {
        Device device = requireDevice(deviceId);
        String channelId = channelOf(device);
        LapiEndpoint endpoint = LapiEndpoint.from(device);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ID", presetIndex);
        body.put("Name", name);
        String path = "/LAPI/V1.0/Channels/" + channelId + "/PTZ/Presets";
        JsonNode resp = overwrite
                ? lapiClient.put(endpoint, path + "/" + presetIndex, body)
                : lapiClient.post(endpoint, path, body);
        ensureLapiOk(resp);
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
        Device device = requireDevice(deviceId);
        String channelId = channelOf(device);
        JsonNode resp = lapiClient.get(LapiEndpoint.from(device),
                "/LAPI/V1.0/Channels/" + channelId + "/Media/Snapshot/1");
        return ok("snapshot", Map.of(
                "deviceId", deviceId,
                "channelType", channelType == null ? "visible" : channelType,
                "response", resp.toString()
        ));
    }

    /** 点按一次：下发动作，短暂保持后发全停。 */
    private Map<String, Object> clickPulse(String deviceId, int cmd, int speed, String action) {
        Map<String, Object> started = ptzCtrl(deviceId, cmd, speed, action);
        try {
            Thread.sleep(CLICK_PULSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        ptzCtrl(deviceId, CMD_STOP, speed, action + "-stop");
        return started;
    }

    private Map<String, Object> ptzCtrl(String deviceId, int cmd, int speed, String action) {
        Device device = requireDevice(deviceId);
        String channelId = channelOf(device);
        int spd = Math.max(1, Math.min(speed, 8));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("PTZCmd", cmd);
        body.put("Para1", spd);
        body.put("Para2", spd);
        body.put("Para3", 0);
        JsonNode resp = lapiClient.put(LapiEndpoint.from(device),
                "/LAPI/V1.0/Channels/" + channelId + "/PTZ/PTZCtrl", body);
        int code = resp.path("Response").path("ResponseCode").asInt(-1);
        if (code != 0) {
            throw new IllegalStateException("LAPI PTZ 失败: " + resp);
        }
        return ok(action, Map.of(
                "deviceId", deviceId,
                "ptzCmd", cmd,
                "speed", spd,
                "response", resp.toString()
        ));
    }

    private Device requireDevice(String deviceId) {
        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("宇视设备不存在: " + deviceId));
        if (device.getHost() == null || device.getHost().isBlank()) {
            throw new IllegalArgumentException("设备未配置宇视地址: " + deviceId);
        }
        return device;
    }

    private static String channelOf(Device device) {
        String channel = device.getAccessChannel();
        return channel == null || channel.isBlank() ? "0" : channel.trim();
    }

    private List<Map<String, Object>> loadPresets(Device device) {
        List<Map<String, Object>> presets = new ArrayList<>();
        String channelId = channelOf(device);
        try {
            JsonNode root = lapiClient.get(LapiEndpoint.from(device),
                    "/LAPI/V1.0/Channels/" + channelId + "/PTZ/Presets");
            JsonNode arr = root.path("Response").path("Data").path("PresetInfos");
            if (!arr.isArray()) {
                return presets;
            }
            for (JsonNode preset : arr) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("index", preset.path("ID").asInt());
                item.put("name", preset.path("Name").asText(""));
                presets.add(item);
            }
        } catch (RuntimeException e) {
            log.info("[Uniview-LAPI] 预置位列表不可用: {}", e.getMessage());
        }
        return presets;
    }

    private static void ensureLapiOk(JsonNode resp) {
        int code = resp.path("Response").path("ResponseCode").asInt(-1);
        if (code != 0) {
            String message = resp.path("Response").path("ResponseString").asText("LAPI 失败");
            throw new IllegalStateException(message);
        }
    }

    private static int directionCmd(String direction) {
        return switch (direction == null ? "" : direction.toLowerCase()) {
            case "up" -> 0x0402;
            case "down" -> 0x0404;
            case "left" -> 0x0504;
            case "right" -> 0x0502;
            case "left_up" -> 0x0702;
            case "left_down" -> 0x0704;
            case "right_up" -> 0x0802;
            case "right_down" -> 0x0804;
            case "stop" -> CMD_STOP;
            default -> throw new IllegalArgumentException("不支持的云台方向: " + direction);
        };
    }

    private static int zoomCmd(String action) {
        return switch (action == null ? "" : action.toLowerCase()) {
            case "in", "zoom_in", "tele" -> CMD_ZOOM_IN;
            case "out", "zoom_out", "wide" -> CMD_ZOOM_OUT;
            default -> throw new IllegalArgumentException("不支持的变倍动作: " + action);
        };
    }

    private static int focusCmd(String action) {
        return switch (action == null ? "" : action.toLowerCase()) {
            case "near", "focus_near" -> CMD_FOCUS_NEAR;
            case "far", "focus_far" -> CMD_FOCUS_FAR;
            default -> throw new IllegalArgumentException("不支持的对焦动作: " + action);
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

package com.jizhi.videomid.uniview;

import com.jizhi.videomid.device.AccessVendor;
import com.jizhi.videomid.device.Device;
import com.jizhi.videomid.device.DevicePtzPresetService;
import com.jizhi.videomid.device.DeviceRepository;
import com.jizhi.videomid.device.VendorDevices;
import com.jizhi.videomid.uniview.live.LiveUniviewPtzAdapter;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 云台按设备平台分发。宇视走摄像机，模拟不打设备，海康明确未对接。
 */
@Service
@Primary
public class VendorPtzGateway implements UniviewPtzPort {

    private static final int CMD_STOP = 0x0901;
    private static final int CMD_ZOOM_IN = 0x0302;
    private static final int CMD_ZOOM_OUT = 0x0304;
    private static final int CMD_FOCUS_NEAR = 0x0202;
    private static final int CMD_FOCUS_FAR = 0x0204;

    private final DeviceRepository deviceRepository;
    private final DevicePtzPresetService presetService;
    private final LiveUniviewPtzAdapter livePtz;

    public VendorPtzGateway(DeviceRepository deviceRepository,
                            DevicePtzPresetService presetService,
                            LiveUniviewPtzAdapter livePtz) {
        this.deviceRepository = deviceRepository;
        this.presetService = presetService;
        this.livePtz = livePtz;
    }

    @Override
    public List<Map<String, Object>> listPtzDevices() {
        List<Map<String, Object>> devices = new ArrayList<>();
        for (Map<String, Object> item : livePtz.listPtzDevices()) {
            String deviceId = String.valueOf(item.get("deviceId"));
            Device device = deviceRepository.findByDeviceId(deviceId).orElse(null);
            if (device != null && VendorDevices.of(device) == AccessVendor.UNIVIEW) {
                devices.add(item);
            }
        }
        for (Device device : deviceRepository.findAll()) {
            if (VendorDevices.of(device) != AccessVendor.MOCK) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("deviceId", device.getDeviceId());
            item.put("name", device.getName());
            item.put("presets", presetService.listMaps(device.getDeviceId()));
            devices.add(item);
        }
        return devices;
    }

    @Override
    public Map<String, Object> move(String deviceId, String direction, int speed) {
        int cmd = directionCmd(direction);
        return route(deviceId, () -> livePtz.move(deviceId, direction, speed),
                () -> mockOk("move", deviceId, Map.of("ptzCmd", cmd, "speed", clampSpeed(speed))));
    }

    @Override
    public Map<String, Object> zoom(String deviceId, String action, int speed) {
        int cmd = zoomCmd(action);
        return route(deviceId, () -> livePtz.zoom(deviceId, action, speed),
                () -> mockOk("zoom", deviceId, Map.of("ptzCmd", cmd, "speed", clampSpeed(speed))));
    }

    @Override
    public Map<String, Object> focus(String deviceId, String action, int speed) {
        int cmd = focusCmd(action);
        return route(deviceId, () -> livePtz.focus(deviceId, action, speed),
                () -> mockOk("focus", deviceId, Map.of("ptzCmd", cmd, "speed", clampSpeed(speed))));
    }

    @Override
    public Map<String, Object> wideAngle(String deviceId) {
        return route(deviceId, () -> livePtz.wideAngle(deviceId),
                () -> mockOk("wide-angle", deviceId, Map.of("ptzCmd", CMD_ZOOM_OUT, "speed", 4)));
    }

    @Override
    public Map<String, Object> gotoPreset(String deviceId, int presetIndex) {
        return route(deviceId, () -> livePtz.gotoPreset(deviceId, presetIndex), () -> {
            presetService.require(deviceId, presetIndex);
            return mockOk("goto-preset", deviceId, Map.of("presetIndex", presetIndex));
        });
    }

    @Override
    public Map<String, Object> setPreset(String deviceId, int presetIndex, String name, boolean overwrite) {
        return route(deviceId, () -> livePtz.setPreset(deviceId, presetIndex, name, overwrite), () -> {
            presetService.save(deviceId, presetIndex, name, null, overwrite);
            return mockOk("set-preset", deviceId, Map.of(
                    "presetIndex", presetIndex,
                    "name", name,
                    "overwrite", overwrite
            ));
        });
    }

    @Override
    public Map<String, Object> snapshot(String deviceId, String channelType) {
        String channel = channelType == null || channelType.isBlank() ? "visible" : channelType;
        return route(deviceId, () -> livePtz.snapshot(deviceId, channel),
                () -> mockOk("snapshot", deviceId, Map.of("channelType", channel)));
    }

    private Map<String, Object> route(String deviceId, java.util.function.Supplier<Map<String, Object>> uniview,
                                      java.util.function.Supplier<Map<String, Object>> mock) {
        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + deviceId));
        return switch (VendorDevices.of(device)) {
            case UNIVIEW -> uniview.get();
            case MOCK -> mock.get();
            case HIKVISION -> throw new IllegalArgumentException(VendorDevices.HIKVISION_UNSUPPORTED);
        };
    }

    private static Map<String, Object> mockOk(String action, String deviceId, Map<String, Object> extra) {
        Map<String, Object> row = new HashMap<>();
        row.put("success", true);
        row.put("mock", true);
        row.put("live", false);
        row.put("action", action);
        row.put("deviceId", deviceId);
        row.put("response", "{\"mock\":true}");
        row.putAll(extra);
        return row;
    }

    private static int clampSpeed(int speed) {
        return Math.max(1, Math.min(speed, 8));
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
}

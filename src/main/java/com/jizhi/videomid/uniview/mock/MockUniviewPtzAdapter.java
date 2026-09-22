package com.jizhi.videomid.uniview.mock;

import com.jizhi.videomid.device.DevicePtzPresetService;
import com.jizhi.videomid.uniview.UniviewPtzPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "uniview.data-source", havingValue = "mock", matchIfMissing = true)
public class MockUniviewPtzAdapter implements UniviewPtzPort {

    private final MockUniviewDataLoader dataLoader;
    private final DevicePtzPresetService presetService;

    public MockUniviewPtzAdapter(MockUniviewDataLoader dataLoader, DevicePtzPresetService presetService) {
        this.dataLoader = dataLoader;
        this.presetService = presetService;
    }

    @Override
    public List<Map<String, Object>> listPtzDevices() {
        return dataLoader.devices().stream().map(d -> {
            String deviceId = String.valueOf(d.get("deviceId"));
            Map<String, Object> m = new HashMap<>();
            m.put("deviceId", deviceId);
            m.put("name", d.get("name"));
            m.put("model", d.get("model"));
            m.put("presets", presetService.listMaps(deviceId));
            return m;
        }).toList();
    }

    @Override
    public Map<String, Object> move(String deviceId, String direction, int speed) {
        dataLoader.requireDevice(deviceId);
        return ok("mock-move", Map.of(
                "deviceId", deviceId,
                "direction", direction,
                "speed", speed
        ));
    }

    @Override
    public Map<String, Object> zoom(String deviceId, String action, int speed) {
        dataLoader.requireDevice(deviceId);
        return ok("mock-zoom", Map.of(
                "deviceId", deviceId,
                "action", action,
                "speed", speed
        ));
    }

    @Override
    public Map<String, Object> focus(String deviceId, String action, int speed) {
        dataLoader.requireDevice(deviceId);
        return ok("mock-focus", Map.of(
                "deviceId", deviceId,
                "action", action,
                "speed", speed
        ));
    }

    @Override
    public Map<String, Object> wideAngle(String deviceId) {
        dataLoader.requireDevice(deviceId);
        return ok("mock-wide-angle", Map.of(
                "deviceId", deviceId,
                "zoom", 1.0
        ));
    }

    @Override
    public Map<String, Object> gotoPreset(String deviceId, int presetIndex) {
        dataLoader.requireDevice(deviceId);
        return ok("mock-goto-preset", Map.of(
                "deviceId", deviceId,
                "presetIndex", presetIndex
        ));
    }

    @Override
    public Map<String, Object> setPreset(String deviceId, int presetIndex, String name, boolean overwrite) {
        dataLoader.requireDevice(deviceId);
        Map<String, Object> data = new HashMap<>(presetService.toMap(
                presetService.save(deviceId, presetIndex, name, null, overwrite)));
        data.put("deviceId", deviceId);
        data.put("presetIndex", presetIndex);
        data.put("overwrite", overwrite);
        return ok("mock-set-preset", data);
    }

    @Override
    public Map<String, Object> snapshot(String deviceId, String channelType) {
        dataLoader.requireDevice(deviceId);
        return ok("mock-snapshot", Map.of(
                "deviceId", deviceId,
                "channelType", channelType == null ? "visible" : channelType,
                "imageUrl", "/api/uniview/mock/snapshot/" + deviceId + ".jpg",
                "mock", true
        ));
    }

    private static Map<String, Object> ok(String action, Map<String, Object> data) {
        Map<String, Object> m = new HashMap<>(data);
        m.put("success", true);
        m.put("mock", true);
        m.put("action", action);
        return m;
    }
}

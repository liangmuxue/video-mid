package com.jizhi.videomid.device;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 中台预置位目录。调用/保存时 live 仍须先打设备。 */
@Service
public class DevicePtzPresetService {

    private final DevicePtzPresetRepository repository;

    public DevicePtzPresetService(DevicePtzPresetRepository repository) {
        this.repository = repository;
    }

    public List<Map<String, Object>> listMaps(String deviceId) {
        return repository.findByDeviceId(deviceId).stream().map(this::toMap).toList();
    }

    public Optional<DevicePtzPreset> find(String deviceId, int presetIndex) {
        return repository.find(deviceId, presetIndex);
    }

    public DevicePtzPreset require(String deviceId, int presetIndex) {
        return find(deviceId, presetIndex)
                .orElseThrow(() -> new IllegalArgumentException("预置位不存在: " + presetIndex));
    }

    public DevicePtzPreset save(String deviceId, int presetIndex, String name, Double zoom, boolean overwrite) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("预置位名称不能为空");
        }
        Optional<DevicePtzPreset> existing = repository.find(deviceId, presetIndex);
        if (existing.isPresent() && !overwrite) {
            throw new IllegalArgumentException("预置位 " + presetIndex + " 已存在，请勾选覆盖");
        }
        DevicePtzPreset row = existing.orElseGet(DevicePtzPreset::new);
        row.setDeviceId(deviceId);
        row.setPresetIndex(presetIndex);
        row.setName(name.trim());
        if (zoom != null) {
            row.setZoom(zoom);
        } else if (row.getZoom() == null) {
            row.setZoom(1.0);
        }
        if (existing.isPresent()) {
            repository.update(row);
        } else {
            repository.insert(row);
        }
        return require(deviceId, presetIndex);
    }

    public Map<String, Object> toMap(DevicePtzPreset p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("index", p.getPresetIndex());
        m.put("name", p.getName());
        return m;
    }
}

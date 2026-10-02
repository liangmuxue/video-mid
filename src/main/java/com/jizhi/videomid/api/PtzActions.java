package com.jizhi.videomid.api;

import com.jizhi.videomid.uniview.UniviewCatalogPort;
import com.jizhi.videomid.uniview.UniviewPtzPort;
import com.jizhi.videomid.uniview.dto.PtzActionRequest;
import com.jizhi.videomid.uniview.dto.PtzDeviceIdRequest;
import com.jizhi.videomid.uniview.dto.PtzMoveRequest;
import com.jizhi.videomid.uniview.dto.PtzPresetGotoRequest;
import com.jizhi.videomid.uniview.dto.PtzPresetSaveRequest;
import com.jizhi.videomid.uniview.dto.PtzSnapshotRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 云台只收设备编号。平台由设备 vendor 决定，调用方不传。
 */
@Service
public class PtzActions {

    private static final int DEFAULT_SPEED = 4;

    private final UniviewPtzPort ptzPort;
    private final UniviewCatalogPort catalogPort;

    public PtzActions(UniviewPtzPort ptzPort, UniviewCatalogPort catalogPort) {
        this.ptzPort = ptzPort;
        this.catalogPort = catalogPort;
    }

    public List<Map<String, Object>> devices() {
        return ptzPort.listPtzDevices();
    }

    public List<Map<String, Object>> catalogDevices() {
        return catalogPort.listDevices();
    }

    public Map<String, Object> device(String deviceId) {
        return catalogPort.getDevice(requireText(deviceId, "deviceId"));
    }

    public Map<String, Object> move(PtzMoveRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        return ptzPort.move(requireText(req.getDeviceId(), "deviceId"),
                requireText(req.getDirection(), "direction"), speedOrDefault(req.getSpeed()));
    }

    public Map<String, Object> zoom(PtzActionRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        return ptzPort.zoom(requireText(req.getDeviceId(), "deviceId"),
                requireText(req.getAction(), "action"), speedOrDefault(req.getSpeed()));
    }

    public Map<String, Object> focus(PtzActionRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        return ptzPort.focus(requireText(req.getDeviceId(), "deviceId"),
                requireText(req.getAction(), "action"), speedOrDefault(req.getSpeed()));
    }

    public Map<String, Object> wideAngle(PtzDeviceIdRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        return ptzPort.wideAngle(requireText(req.getDeviceId(), "deviceId"));
    }

    public Map<String, Object> gotoPreset(PtzPresetGotoRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (req.getIndex() == null) {
            throw new IllegalArgumentException("index 不能为空");
        }
        return ptzPort.gotoPreset(requireText(req.getDeviceId(), "deviceId"), req.getIndex());
    }

    public Map<String, Object> setPreset(PtzPresetSaveRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (req.getIndex() == null) {
            throw new IllegalArgumentException("index 不能为空");
        }
        boolean overwrite = Boolean.TRUE.equals(req.getOverwrite());
        return ptzPort.setPreset(requireText(req.getDeviceId(), "deviceId"),
                req.getIndex(), requireText(req.getName(), "name"), overwrite);
    }

    public Map<String, Object> snapshot(PtzSnapshotRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String channelType = req.getChannelType() == null || req.getChannelType().isBlank()
                ? "visible" : req.getChannelType().trim();
        return ptzPort.snapshot(requireText(req.getDeviceId(), "deviceId"), channelType);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }

    private static int speedOrDefault(Integer speed) {
        return speed == null ? DEFAULT_SPEED : speed;
    }
}

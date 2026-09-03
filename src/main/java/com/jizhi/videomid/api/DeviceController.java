package com.jizhi.videomid.api;

import com.jizhi.videomid.api.dto.ApiResponse;
import com.jizhi.videomid.device.entity.DeviceEntity;
import com.jizhi.videomid.device.service.DeviceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /devices — 设备列表，全部读取 MySQL 设备表。
 */
@RestController
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping("/devices")
    public ApiResponse<List<Map<String, Object>>> listDevices() {
        List<DeviceEntity> entities = deviceService.listAll();
        List<Map<String, Object>> list = new ArrayList<>();
        for (DeviceEntity e : entities) {
            Map<String, Object> m = new HashMap<>();
            m.put("deviceId", e.getDeviceId());
            m.put("parentId", e.getParentId());
            m.put("name", e.getName());
            m.put("manufacturer", e.getManufacturer());
            m.put("model", e.getModel());
            m.put("status", e.getStatus());
            m.put("online", deviceService.isOnline(e.getDeviceId()) || "ON".equalsIgnoreCase(e.getStatus()));
            m.put("deviceType", e.getDeviceType());
            m.put("streamType", e.getStreamType());
            m.put("mainChannelId", e.getMainChannelId());
            m.put("subChannelId", e.getSubChannelId());
            m.put("ptzType", e.getPtzType());
            m.put("gatewayId", e.getGatewayId());
            m.put("platformId", e.getPlatformId());
            m.put("address", e.getAddress());
            m.put("civilCode", e.getCivilCode());
            m.put("longitude", e.getLongitude());
            m.put("latitude", e.getLatitude());
            list.add(m);
        }
        return ApiResponse.ok(list);
    }
}

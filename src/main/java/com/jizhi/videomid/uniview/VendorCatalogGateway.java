package com.jizhi.videomid.uniview;

import com.jizhi.videomid.device.AccessVendor;
import com.jizhi.videomid.device.Device;
import com.jizhi.videomid.device.DeviceRepository;
import com.jizhi.videomid.device.DeviceStream;
import com.jizhi.videomid.device.DeviceStreamRepository;
import com.jizhi.videomid.device.VendorDevices;
import com.jizhi.videomid.uniview.live.LiveUniviewCatalogAdapter;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 云台设备详情按平台取码流。宇视读摄像机码流表，模拟读已登记码流，海康未对接。
 */
@Service
@Primary
public class VendorCatalogGateway implements UniviewCatalogPort {

    private final DeviceRepository deviceRepository;
    private final DeviceStreamRepository streamRepository;
    private final LiveUniviewCatalogAdapter liveCatalog;

    public VendorCatalogGateway(DeviceRepository deviceRepository,
                                DeviceStreamRepository streamRepository,
                                LiveUniviewCatalogAdapter liveCatalog) {
        this.deviceRepository = deviceRepository;
        this.streamRepository = streamRepository;
        this.liveCatalog = liveCatalog;
    }

    @Override
    public List<Map<String, Object>> listDevices() {
        List<Map<String, Object>> devices = new ArrayList<>();
        for (Map<String, Object> item : liveCatalog.listDevices()) {
            Device device = deviceRepository.findByDeviceId(String.valueOf(item.get("deviceId"))).orElse(null);
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
            devices.add(item);
        }
        return devices;
    }

    @Override
    public Map<String, Object> getDevice(String deviceId) {
        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + deviceId));
        return switch (VendorDevices.of(device)) {
            case UNIVIEW -> liveCatalog.getDevice(deviceId);
            case MOCK -> mockView(device);
            case HIKVISION -> throw new IllegalArgumentException(VendorDevices.HIKVISION_UNSUPPORTED);
        };
    }

    private Map<String, Object> mockView(Device device) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("deviceId", device.getDeviceId());
        view.put("name", device.getName());
        List<Map<String, Object>> streams = new ArrayList<>();
        for (DeviceStream stream : streamRepository.findByDeviceId(device.getDeviceId())) {
            String type = stream.getStreamType() == null ? "" : stream.getStreamType();
            if (!"main".equalsIgnoreCase(type) && !"sub".equalsIgnoreCase(type) && !"third".equalsIgnoreCase(type)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("channelType", "visible");
            item.put("streamType", stream.getStreamType());
            item.put("streamUrl", stream.getStreamUrl());
            streams.add(item);
        }
        view.put("streams", streams);
        return view;
    }
}

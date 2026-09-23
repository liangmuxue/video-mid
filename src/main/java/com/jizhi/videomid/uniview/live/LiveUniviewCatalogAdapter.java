package com.jizhi.videomid.uniview.live;

import com.jizhi.videomid.device.Device;
import com.jizhi.videomid.device.DeviceRepository;
import com.jizhi.videomid.device.DeviceStream;
import com.jizhi.videomid.device.DeviceStreamRepository;
import com.jizhi.videomid.uniview.UniviewCatalogPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "uniview.data-source", havingValue = "live")
public class LiveUniviewCatalogAdapter implements UniviewCatalogPort {

    private final DeviceRepository deviceRepository;
    private final DeviceStreamRepository streamRepository;

    public LiveUniviewCatalogAdapter(DeviceRepository deviceRepository, DeviceStreamRepository streamRepository) {
        this.deviceRepository = deviceRepository;
        this.streamRepository = streamRepository;
    }

    @Override
    public List<Map<String, Object>> listDevices() {
        List<Map<String, Object>> devices = new ArrayList<>();
        for (Device device : deviceRepository.findAll()) {
            if (device.getHost() == null || device.getHost().isBlank()) {
                continue;
            }
            devices.add(toView(device, false));
        }
        return devices;
    }

    @Override
    public Map<String, Object> getDevice(String deviceId) {
        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("宇视设备不存在: " + deviceId));
        if (device.getHost() == null || device.getHost().isBlank()) {
            throw new IllegalArgumentException("设备未配置宇视地址: " + deviceId);
        }
        return toView(device, true);
    }

    private Map<String, Object> toView(Device device, boolean withStreams) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("deviceId", device.getDeviceId());
        view.put("name", device.getName());
        view.put("manufacturer", device.getManufacturer());
        view.put("model", device.getModel());
        view.put("ptzType", device.getPtzType());
        view.put("host", device.getHost());
        view.put("port", device.getPort());
        view.put("accessChannel", device.getAccessChannel());
        view.put("accessStatus", device.getAccessStatus());
        if (!withStreams) {
            return view;
        }
        List<Map<String, Object>> streams = new ArrayList<>();
        for (DeviceStream stream : streamRepository.findByDeviceId(device.getDeviceId())) {
            String type = stream.getStreamType() == null ? "" : stream.getStreamType();
            if (!"main".equalsIgnoreCase(type) && !"sub".equalsIgnoreCase(type) && !"third".equalsIgnoreCase(type)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("channelId", device.getAccessChannel() == null ? "0" : device.getAccessChannel());
            item.put("channelType", "visible");
            item.put("streamType", stream.getStreamType());
            item.put("streamName", stream.getStreamName());
            item.put("streamUrl", stream.getStreamUrl());
            item.put("status", stream.getStatus());
            streams.add(item);
        }
        view.put("streams", streams);
        view.put("streamCount", streams.size());
        return view;
    }
}

package com.jizhi.videomid.uniview.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.jizhi.videomid.uniview.UniviewCatalogPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "uniview.data-source", havingValue = "live")
public class LiveUniviewCatalogAdapter implements UniviewCatalogPort {

    private final UniviewLapiClient lapiClient;

    public LiveUniviewCatalogAdapter(UniviewLapiClient lapiClient) {
        this.lapiClient = lapiClient;
    }

    @Override
    public List<Map<String, Object>> listDevices() {
        if (!lapiClient.isConfigured()) {
            throw notConfigured();
        }
        JsonNode root = lapiClient.get("/LAPI/V1.0/Channels/System/ChannelDetailInfos");
        return lapiClient.parseDevicesFromChannels(root);
    }

    @Override
    public Map<String, Object> getDevice(String deviceId) {
        for (Map<String, Object> d : listDevices()) {
            if (deviceId.equals(String.valueOf(d.get("deviceId")))) {
                return new HashMap<>(d);
            }
        }
        throw new IllegalArgumentException("宇视设备不存在: " + deviceId);
    }

    private static IllegalStateException notConfigured() {
        return new IllegalStateException(
                "宇视 live 未配置：请设置 uniview.live.device-host / username / password");
    }
}

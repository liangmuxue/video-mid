package com.jizhi.videomid.gb28181.live;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** live 模式：SIP REGISTER 后的下级设备/通道注册表（开发期可由 DB 种子填充） */
@Component
public class Gb28181DeviceRegistry {

    private final ConcurrentHashMap<String, Map<String, Object>> devices = new ConcurrentHashMap<>();

    public void upsertDevice(Map<String, Object> deviceRow) {
        if (deviceRow == null || deviceRow.get("deviceId") == null) {
            return;
        }
        String deviceId = String.valueOf(deviceRow.get("deviceId"));
        devices.put(deviceId, new LinkedHashMap<>(deviceRow));
    }

    public void clear() {
        devices.clear();
    }

    public int deviceCount() {
        return devices.size();
    }

    public List<Map<String, Object>> listCatalog() {
        return new ArrayList<>(devices.values());
    }

    public Optional<Map<String, Object>> findDevice(String deviceId) {
        Map<String, Object> d = devices.get(deviceId);
        return d == null ? Optional.empty() : Optional.of(d);
    }

    public Optional<Map<String, Object>> findByGbDeviceId(String gbDeviceId) {
        if (gbDeviceId == null || gbDeviceId.isBlank()) {
            return Optional.empty();
        }
        String id = gbDeviceId.trim();
        for (Map<String, Object> d : devices.values()) {
            if (id.equals(String.valueOf(d.get("gbDeviceId")))) {
                return Optional.of(d);
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> findChannel(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return Optional.empty();
        }
        String id = channelId.trim();
        for (Map<String, Object> d : devices.values()) {
            Object chs = d.get("channels");
            if (!(chs instanceof List<?> list)) {
                continue;
            }
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> ch)) {
                    continue;
                }
                if (id.equals(String.valueOf(ch.get("channelId")))) {
                    Map<String, Object> m = new LinkedHashMap<>((Map<String, Object>) ch);
                    m.put("deviceId", d.get("deviceId"));
                    m.put("gbDeviceId", d.get("gbDeviceId"));
                    m.put("deviceName", d.get("name"));
                    return Optional.of(m);
                }
            }
        }
        return Optional.empty();
    }
}

package com.jizhi.videomid.gb28181.sip.live;

import com.jizhi.videomid.gb28181.live.Gb28181DeviceRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将 SIP Catalog Response 合并进设备注册表 */
@Component
@ConditionalOnProperty(name = "gb28181.data-source", havingValue = "live")
public class Gb28181CatalogMerger {

    private final Gb28181DeviceRegistry registry;

    public Gb28181CatalogMerger(Gb28181DeviceRegistry registry) {
        this.registry = registry;
    }

    public void mergeCatalogResponse(String rootDeviceId, List<Map<String, String>> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Map<String, Map<String, Object>> devices = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> channelsByParent = new LinkedHashMap<>();

        for (Map<String, String> item : items) {
            String id = item.get("DeviceID");
            if (id == null || id.isBlank()) {
                continue;
            }
            String parentId = item.get("ParentID");
            if (parentId == null || parentId.isBlank()) {
                Map<String, Object> dev = new LinkedHashMap<>();
                dev.put("deviceId", id);
                dev.put("gbDeviceId", id);
                dev.put("name", item.getOrDefault("Name", id));
                dev.put("status", item.getOrDefault("Status", "ON"));
                dev.put("registered", true);
                dev.put("mock", false);
                dev.put("source", "sip-catalog");
                devices.put(id, dev);
            } else {
                Map<String, Object> ch = new LinkedHashMap<>();
                ch.put("channelId", id);
                ch.put("streamName", item.getOrDefault("Name", id));
                ch.put("gbStatus", item.getOrDefault("Status", "ON"));
                channelsByParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(ch);
            }
        }

        if (devices.isEmpty() && rootDeviceId != null && !rootDeviceId.isBlank()) {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("deviceId", rootDeviceId);
            root.put("gbDeviceId", rootDeviceId);
            root.put("name", rootDeviceId);
            root.put("status", "ON");
            root.put("registered", true);
            root.put("mock", false);
            root.put("source", "sip-catalog");
            devices.put(rootDeviceId, root);
        }

        for (Map.Entry<String, List<Map<String, Object>>> e : channelsByParent.entrySet()) {
            String parentGbId = e.getKey();
            Map<String, Object> dev = new LinkedHashMap<>();
            registry.findByGbDeviceId(parentGbId).ifPresentOrElse(dev::putAll, () -> {
                Map<String, Object> created = devices.computeIfAbsent(parentGbId, id -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("deviceId", id);
                    m.put("gbDeviceId", id);
                    m.put("name", id);
                    m.put("status", "ON");
                    m.put("registered", true);
                    m.put("mock", false);
                    m.put("source", "sip-catalog");
                    return m;
                });
                dev.putAll(created);
            });
            dev.put("channels", e.getValue());
            dev.put("source", "sip-catalog");
            registry.upsertDevice(dev);
        }

        for (Map<String, Object> dev : devices.values()) {
            if (!dev.containsKey("channels")) {
                dev.put("channels", List.of());
            }
            registry.upsertDevice(dev);
        }
    }
}

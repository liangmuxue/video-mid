package com.jizhi.videomid.gb28181;

import com.jizhi.videomid.device.Device;
import com.jizhi.videomid.device.DeviceRepository;
import com.jizhi.videomid.device.DeviceStream;
import com.jizhi.videomid.device.DeviceStreamRepository;
import com.jizhi.videomid.mock.MockCatalogSyncRules;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 校验业务库与国标 Catalog 是否一致。mock 多余设备规则由 MockCatalogSyncRules 提供。 */
@Service
public class Gb28181SyncService {

    private final Gb28181CatalogPort catalogPort;
    private final DeviceRepository deviceRepository;
    private final DeviceStreamRepository streamRepository;
    private final ObjectProvider<MockCatalogSyncRules> mockSyncRules;

    public Gb28181SyncService(Gb28181CatalogPort catalogPort,
                              DeviceRepository deviceRepository,
                              DeviceStreamRepository streamRepository,
                              ObjectProvider<MockCatalogSyncRules> mockSyncRules) {
        this.catalogPort = catalogPort;
        this.deviceRepository = deviceRepository;
        this.streamRepository = streamRepository;
        this.mockSyncRules = mockSyncRules;
    }

    public Map<String, Object> verify() {
        List<Map<String, Object>> catalog = catalogPort.listCatalog();
        Set<String> catalogDeviceIds = new HashSet<>();
        Set<String> catalogChannelIds = new HashSet<>();
        for (Map<String, Object> row : catalog) {
            catalogDeviceIds.add(String.valueOf(row.get("deviceId")));
            Object chs = row.get("channels");
            if (chs instanceof List<?> list) {
                for (Object c : list) {
                    if (c instanceof Map<?, ?> m) {
                        catalogChannelIds.add(String.valueOf(m.get("channelId")));
                    }
                }
            }
        }

        List<String> missingDevices = new ArrayList<>();
        List<String> extraDevices = new ArrayList<>();
        List<String> missingChannels = new ArrayList<>();
        List<String> extraChannels = new ArrayList<>();

        Set<String> dbDeviceIds = new HashSet<>();
        Set<String> dbChannelIds = new HashSet<>();
        for (Device d : deviceRepository.findAll()) {
            dbDeviceIds.add(d.getDeviceId());
            for (DeviceStream s : streamRepository.findByDeviceId(d.getDeviceId())) {
                if (s.getChannelId() != null && !s.getChannelId().isBlank()) {
                    dbChannelIds.add(s.getChannelId());
                }
            }
        }

        for (String id : catalogDeviceIds) {
            if (!dbDeviceIds.contains(id)) {
                missingDevices.add(id);
            }
        }
        MockCatalogSyncRules mockRules = mockSyncRules.getIfAvailable();
        for (String id : dbDeviceIds) {
            if (catalogDeviceIds.contains(id)) {
                continue;
            }
            if (mockRules == null || mockRules.countDbDeviceAsExtra(id)) {
                extraDevices.add(id);
            }
        }
        for (String id : catalogChannelIds) {
            if (!dbChannelIds.contains(id)) {
                missingChannels.add(id);
            }
        }
        for (String id : dbChannelIds) {
            if (!catalogChannelIds.contains(id)) {
                extraChannels.add(id);
            }
        }

        boolean ok = missingDevices.isEmpty() && missingChannels.isEmpty()
                && extraDevices.isEmpty() && extraChannels.isEmpty();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", ok);
        result.put("catalogDeviceCount", catalogDeviceIds.size());
        result.put("catalogChannelCount", catalogChannelIds.size());
        result.put("dbDeviceCount", dbDeviceIds.size());
        result.put("dbChannelCount", dbChannelIds.size());
        result.put("missingDevices", missingDevices);
        result.put("extraDevices", extraDevices);
        result.put("missingChannels", missingChannels);
        result.put("extraChannels", extraChannels);
        return result;
    }

    public List<Map<String, Object>> bizAlignedDevices() {
        List<Map<String, Object>> catalog = catalogPort.listCatalog();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : catalog) {
            String deviceId = String.valueOf(row.get("deviceId"));
            Device db = deviceRepository.findByDeviceId(deviceId).orElse(null);
            Map<String, Object> m = new HashMap<>(row);
            if (db != null) {
                m.put("status", db.getStatus());
                m.put("folderId", db.getFolderId());
            } else {
                m.put("status", 1);
            }
            m.put("gbRegistered", true);
            m.put("playable", true);
            out.add(m);
        }
        return out;
    }
}

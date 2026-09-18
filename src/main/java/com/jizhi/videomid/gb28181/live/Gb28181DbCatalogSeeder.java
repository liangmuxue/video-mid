package com.jizhi.videomid.gb28181.live;

import com.jizhi.videomid.config.Gb28181Properties;
import com.jizhi.videomid.device.Device;
import com.jizhi.videomid.device.DeviceRepository;
import com.jizhi.videomid.device.DeviceStream;
import com.jizhi.videomid.device.DeviceStreamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 从 MySQL 设备/码流表填充 live Catalog 注册表（SIP 未接入前的开发兜底） */
@Component
public class Gb28181DbCatalogSeeder {

    private static final Logger log = LoggerFactory.getLogger(Gb28181DbCatalogSeeder.class);

    private final Gb28181Properties props;
    private final DeviceRepository deviceRepository;
    private final DeviceStreamRepository streamRepository;
    private final Gb28181DeviceRegistry registry;

    public Gb28181DbCatalogSeeder(Gb28181Properties props,
                                  DeviceRepository deviceRepository,
                                  DeviceStreamRepository streamRepository,
                                  Gb28181DeviceRegistry registry) {
        this.props = props;
        this.deviceRepository = deviceRepository;
        this.streamRepository = streamRepository;
        this.registry = registry;
    }

    public int seedFromDb() {
        registry.clear();
        int count = 0;
        for (Device d : deviceRepository.findAll()) {
            List<DeviceStream> streams = streamRepository.findByDeviceId(d.getDeviceId());
            List<Map<String, Object>> channels = new ArrayList<>();
            for (DeviceStream s : streams) {
                if (s.getChannelId() == null || s.getChannelId().isBlank()) {
                    continue;
                }
                Map<String, Object> ch = new LinkedHashMap<>();
                ch.put("channelId", s.getChannelId());
                ch.put("streamType", s.getStreamType());
                ch.put("streamName", s.getStreamName() != null ? s.getStreamName() : s.getStreamType());
                ch.put("gbStatus", "ON");
                ch.put("streamUrl", s.getStreamUrl());
                ch.put("previewKey", previewKeyFromStreamUrl(s.getStreamUrl()));
                channels.add(ch);
            }
            if (channels.isEmpty()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("deviceId", d.getDeviceId());
            row.put("name", d.getName());
            row.put("gbDeviceId", gbDeviceId(d));
            row.put("manufacturer", d.getManufacturer());
            row.put("model", d.getModel());
            row.put("status", "ON");
            row.put("registered", true);
            row.put("mock", false);
            row.put("source", "db-seed");
            row.put("channels", channels);
            registry.upsertDevice(row);
            count++;
        }
        log.info("[GB28181-live] DB 种子填充完成 devices={} seedFromDb={}", count, props.getLive().isSeedFromDb());
        return count;
    }

    private static String gbDeviceId(Device d) {
        if (d.getPlatformId() != null && !d.getPlatformId().isBlank()) {
            return d.getPlatformId();
        }
        return d.getDeviceId();
    }

    static String previewKeyFromStreamUrl(String streamUrl) {
        if (streamUrl == null || streamUrl.isBlank()) {
            return "";
        }
        String u = streamUrl.trim();
        int slash = u.lastIndexOf('/');
        String tail = slash >= 0 ? u.substring(slash + 1) : u;
        if (tail.endsWith(".live.flv")) {
            return tail.substring(0, tail.length() - ".live.flv".length());
        }
        if (tail.endsWith(".flv")) {
            return tail.substring(0, tail.length() - ".flv".length());
        }
        return tail;
    }
}

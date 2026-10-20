package com.jizhi.videomid.uniview.cloud;

import com.fasterxml.jackson.databind.JsonNode;
import com.jizhi.videomid.device.AccessVendor;
import com.jizhi.videomid.device.Device;
import com.jizhi.videomid.device.DeviceFolder;
import com.jizhi.videomid.device.DeviceFolderRepository;
import com.jizhi.videomid.device.DeviceFolderService;
import com.jizhi.videomid.device.DeviceRepository;
import com.jizhi.videomid.device.DeviceFeature;
import com.jizhi.videomid.device.DeviceStatus;
import com.jizhi.videomid.device.DeviceStream;
import com.jizhi.videomid.device.DeviceStreamRepository;
import com.jizhi.videomid.device.dto.DeviceFolderRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 把宇视云上的通道写入本地设备表。楼号挂在「园区」下，设备放进对应楼号。已有目录和已停用不改。 */
@Service
public class UniviewCloudSyncService {

    private static final Logger log = LoggerFactory.getLogger(UniviewCloudSyncService.class);
    static final String APP = "cloud";
    /**
     * 不是播放地址。旧版推流巡检看到空地址会把设备改成不可用，
     * 这里放一个不会被解析成流地址的占位，巡检就跳过。
     */
    public static final String STREAM_PLACEHOLDER = "cloud";
    private static final String PARK_NAME = "园区";

    private final UniviewCloudProperties properties;
    private final UniviewCloudClient client;
    private final DeviceRepository deviceRepository;
    private final DeviceStreamRepository streamRepository;
    private final DeviceFolderRepository folderRepository;
    private final DeviceFolderService folderService;

    public UniviewCloudSyncService(UniviewCloudProperties properties,
                                   UniviewCloudClient client,
                                   DeviceRepository deviceRepository,
                                   DeviceStreamRepository streamRepository,
                                   DeviceFolderRepository folderRepository,
                                   DeviceFolderService folderService) {
        this.properties = properties;
        this.client = client;
        this.deviceRepository = deviceRepository;
        this.streamRepository = streamRepository;
        this.folderRepository = folderRepository;
        this.folderService = folderService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnReady() {
        if (!properties.configured()) {
            return;
        }
        try {
            Map<String, Object> result = sync();
            log.info("[宇视云] 启动同步完成 created={} updated={} skipped={}",
                    result.get("created"), result.get("updated"), result.get("skipped"));
        } catch (Exception e) {
            log.warn("[宇视云] 启动同步失败: {}", e.getMessage());
        }
    }

    public Map<String, Object> sync() {
        client.requireConfigured();
        shieldStreams();
        Long parkId = ensurePark();
        Map<String, Long> buildings = loadBuildings(parkId);
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (JsonNode device : pages("/openapi/device/list", "deviceList", Map.of())) {
            String serial = device.path("deviceSerial").asText("").trim();
            if (serial.isEmpty()) {
                continue;
            }
            String deviceName = text(device, "deviceName", serial);
            String model = text(device, "deviceModel", "");
            int deviceOnline = device.path("status").asInt(0);
            List<JsonNode> channels;
            try {
                channels = pages("/openapi/device/channel/list", "channelList",
                        Map.of("deviceSerial", serial));
            } catch (RuntimeException e) {
                skipped++;
                log.warn("[宇视云] 单台同步失败 serial={} err={}", serial, e.getMessage());
                continue;
            }
            if (channels.isEmpty()) {
                created += upsert(serial, 0, deviceName, model, deviceOnline, parkId, buildings) ? 1 : 0;
                continue;
            }
            for (JsonNode channel : channels) {
                int channelNo = channel.path("channelNo").asInt(0);
                String name = channels.size() == 1 ? deviceName : text(channel, "channelName", deviceName);
                int online = channel.hasNonNull("status") ? channel.path("status").asInt(deviceOnline) : deviceOnline;
                String deviceId = UniviewCloudDeviceIds.id(serial, channelNo);
                var existing = deviceRepository.findByDeviceId(deviceId);
                if (existing.isPresent() && !AccessVendor.UNIVIEW_CLOUD.name().equals(existing.get().getVendor())) {
                    skipped++;
                    log.warn("[宇视云] 编号已被其他平台占用，跳过 deviceId={}", deviceId);
                    continue;
                }
                Long folderId = buildingFolder(name, parkId, buildings);
                if (existing.isEmpty()) {
                    insert(deviceId, name, model, online, folderId);
                    created++;
                } else {
                    refresh(existing.get(), model, online, folderId);
                    updated++;
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", created);
        result.put("updated", updated);
        result.put("skipped", skipped);
        return result;
    }

    private boolean upsert(String serial, int channelNo, String name, String model, int online,
                           Long parkId, Map<String, Long> buildings) {
        String deviceId = UniviewCloudDeviceIds.id(serial, channelNo);
        var existing = deviceRepository.findByDeviceId(deviceId);
        if (existing.isPresent() && !AccessVendor.UNIVIEW_CLOUD.name().equals(existing.get().getVendor())) {
            log.warn("[宇视云] 编号已被其他平台占用，跳过 deviceId={}", deviceId);
            return false;
        }
        Long folderId = buildingFolder(name, parkId, buildings);
        if (existing.isEmpty()) {
            insert(deviceId, name, model, online, folderId);
            return true;
        }
        refresh(existing.get(), model, online, folderId);
        return false;
    }

    private void insert(String deviceId, String name, String model, int online, Long folderId) {
        Device created = new Device();
        created.setDeviceId(deviceId);
        created.setDeviceFeature(DeviceFeature.VIDEO);
        created.setName(name);
        created.setVendor(AccessVendor.UNIVIEW_CLOUD.name());
        created.setStatus(online == 1 ? DeviceStatus.ENABLED : DeviceStatus.UNAVAILABLE);
        created.setManufacturer("UNIVIEW");
        created.setModel(model);
        created.setPtzType(1);
        created.setFolderId(folderId);
        created.setDeviceNo(deviceNoText(name));
        long id = deviceRepository.insert(created);
        created.setId(id);
        ensureStream(deviceId, name);
        log.info("[宇视云] 添加设备 deviceId={} name={} folderId={} status={}",
                deviceId, name, folderId, created.getStatus());
    }

    private void refresh(Device existing, String model, int online, Long folderId) {
        if (model != null && !model.isBlank()) {
            existing.setModel(model);
        }
        if (!DeviceStatus.isDisabled(DeviceStatus.normalize(existing.getStatus()))) {
            existing.setStatus(online == 1 ? DeviceStatus.ENABLED : DeviceStatus.UNAVAILABLE);
        }
        if (existing.getManufacturer() == null || existing.getManufacturer().isBlank()) {
            existing.setManufacturer("UNIVIEW");
        }
        if (existing.getFolderId() == null && folderId != null) {
            existing.setFolderId(folderId);
        }
        if (existing.getDeviceNo() == null || existing.getDeviceNo().isBlank()) {
            existing.setDeviceNo(deviceNoText(existing.getName()));
        }
        deviceRepository.update(existing);
        ensureStream(existing.getDeviceId(), existing.getName());
    }

    /** 先补占位地址，再写在线状态，避免同步过程中被旧巡检改回不可用。 */
    private void shieldStreams() {
        for (Device device : deviceRepository.findAll()) {
            if (!AccessVendor.UNIVIEW_CLOUD.name().equals(device.getVendor())) {
                continue;
            }
            ensureStream(device.getDeviceId(), device.getName());
        }
    }

    private void ensureStream(String deviceId, String name) {
        var found = streamRepository.findByDeviceIdAndType(deviceId, "main");
        if (found.isPresent()) {
            DeviceStream stream = found.get();
            if (stream.getStreamUrl() == null || stream.getStreamUrl().isBlank()) {
                stream.setStreamUrl(STREAM_PLACEHOLDER);
                streamRepository.update(stream);
            }
            return;
        }
        UniviewCloudDeviceIds.Target target = UniviewCloudDeviceIds.parse(deviceId);
        DeviceStream stream = new DeviceStream();
        stream.setDeviceId(deviceId);
        stream.setStreamType("main");
        stream.setStreamName(name == null || name.isBlank() ? deviceId : name);
        stream.setStreamUrl(STREAM_PLACEHOLDER);
        stream.setStatus("OFF");
        stream.setSortNo(1);
        stream.setLiveEnabled(true);
        stream.setZlmApp(APP);
        stream.setZlmStream(UniviewCloudDeviceIds.zlmStream(target.serial(), target.channelNo()));
        streamRepository.insert(stream);
    }

    private List<JsonNode> pages(String path, String listField, Map<String, ?> extra) {
        List<JsonNode> all = new ArrayList<>();
        int start = 0;
        int size = 100;
        while (start < 10_000) {
            Map<String, Object> body = new LinkedHashMap<>();
            if (extra != null) {
                body.putAll(extra);
            }
            body.put("pageStart", start);
            body.put("pageSize", size);
            JsonNode data = client.post(path, body);
            JsonNode list = data.path(listField);
            int count = list.isArray() ? list.size() : 0;
            if (count == 0) {
                break;
            }
            list.forEach(all::add);
            int total = data.path("total").asInt(all.size());
            start += count;
            if (start >= total || count < size) {
                break;
            }
        }
        return all;
    }

    private Long ensurePark() {
        for (DeviceFolder folder : folderRepository.findByParentId(null)) {
            if (PARK_NAME.equals(folder.getName())) {
                return folder.getId();
            }
        }
        return createFolder(null, PARK_NAME, 1);
    }

    private Map<String, Long> loadBuildings(Long parkId) {
        Map<String, Long> buildings = new HashMap<>();
        for (DeviceFolder folder : folderRepository.findByParentId(parkId)) {
            buildings.put(folder.getName(), folder.getId());
        }
        return buildings;
    }

    private Long buildingFolder(String deviceName, Long parkId, Map<String, Long> buildings) {
        String building = UniviewCloudBuildingNames.of(deviceName);
        if (building == null) {
            log.info("[宇视云] 名称对不上楼号，不归目录 name={}", deviceName);
            return null;
        }
        Long existing = buildings.get(building);
        if (existing != null) {
            return existing;
        }
        Long created = createFolder(parkId, building, UniviewCloudBuildingNames.sortNo(building));
        buildings.put(building, created);
        log.info("[宇视云] 添加楼号目录 name={} id={}", building, created);
        return created;
    }

    private Long createFolder(Long parentId, String name, int sortNo) {
        DeviceFolderRequest request = new DeviceFolderRequest();
        request.setParentId(parentId);
        request.setName(name);
        request.setSortNo(sortNo);
        Object id = folderService.create(request).get("id");
        return ((Number) id).longValue();
    }

    private static String deviceNoText(String name) {
        Integer deviceNo = UniviewCloudDeviceNos.of(name);
        return deviceNo == null ? null : String.valueOf(deviceNo);
    }

    private static String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}

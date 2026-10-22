package com.jizhi.videomid.uniview.cloud;

import com.fasterxml.jackson.databind.JsonNode;
import com.jizhi.videomid.config.ZlmProperties;
import com.jizhi.videomid.device.AccessVendor;
import com.jizhi.videomid.device.Device;
import com.jizhi.videomid.device.DeviceRepository;
import com.jizhi.videomid.device.DeviceStatus;
import com.jizhi.videomid.device.DeviceStream;
import com.jizhi.videomid.device.DeviceStreamRepository;
import com.jizhi.videomid.device.VendorDevices;
import com.jizhi.videomid.media.ZlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 向宇视云要辅码流地址，打开云端直播后由 ZLM 拉流。
 * 云上的直播开关没打开时，先给这台设备打开。
 */
@Service
public class UniviewCloudPlayService {

    private static final Logger log = LoggerFactory.getLogger(UniviewCloudPlayService.class);

    private final UniviewCloudProperties properties;
    private final UniviewCloudClient client;
    private final ZlmClient zlmClient;
    private final ZlmProperties zlmProperties;
    private final DeviceStreamRepository streamRepository;
    private final DeviceRepository deviceRepository;
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    public UniviewCloudPlayService(UniviewCloudProperties properties,
                                   UniviewCloudClient client,
                                   ZlmClient zlmClient,
                                   ZlmProperties zlmProperties,
                                   DeviceStreamRepository streamRepository,
                                   DeviceRepository deviceRepository) {
        this.properties = properties;
        this.client = client;
        this.zlmClient = zlmClient;
        this.zlmProperties = zlmProperties;
        this.streamRepository = streamRepository;
        this.deviceRepository = deviceRepository;
    }

    /**
     * 列表里给出的 RTSP。主机和端口来自配置，路径按设备编号拼接，不写入码流表。
     */
    public String listedPlayUrl(String deviceId) {
        UniviewCloudDeviceIds.Target target = UniviewCloudDeviceIds.parse(deviceId);
        String base = zlmProperties.getRtspBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.isBlank()) {
            return null;
        }
        String streamKey = UniviewCloudDeviceIds.zlmStream(target.serial(), target.channelNo());
        return base + "/" + UniviewCloudSyncService.APP + "/" + streamKey;
    }

    /**
     * 播放器直接打开列表里的 RTSP 时，由流媒体回调到这里再向云端拉流。
     */
    public void ensurePulled(String app, String zlmStream) {
        if (!UniviewCloudSyncService.APP.equals(app)) {
            return;
        }
        String deviceId;
        try {
            deviceId = UniviewCloudDeviceIds.deviceIdFromZlmStream(zlmStream);
        } catch (IllegalArgumentException e) {
            log.info("[宇视云] 忽略无法识别的播放请求 stream={}", zlmStream);
            return;
        }
        Device device = deviceRepository.findByDeviceId(deviceId).orElse(null);
        if (device == null || VendorDevices.of(device) != AccessVendor.UNIVIEW_CLOUD) {
            log.info("[宇视云] 播放请求没有对应设备 stream={}", zlmStream);
            return;
        }
        if (!DeviceStatus.isEnabled(DeviceStatus.normalize(device.getStatus()))) {
            throw new IllegalArgumentException("仅「已启用」设备可直播");
        }
        startLive(deviceId);
    }

    public Map<String, Object> startLive(String deviceId) {
        UniviewCloudDeviceIds.Target target = UniviewCloudDeviceIds.parse(deviceId);
        synchronized (sessions) {
            Session current = sessions.get(deviceId);
            if (current != null && stillOnline(current)) {
                return view(deviceId, current);
            }
            if (current != null) {
                sessions.remove(deviceId);
                zlmClient.delStreamProxy(UniviewCloudSyncService.APP, current.zlmStream());
                stopCloud(current.liveId());
            }
            enableLive(target.serial());
            Device device = deviceRepository.findByDeviceId(deviceId).orElse(null);
            String source = liveUrl(target, device == null ? null : device.getName());
            JsonNode started = client.post("/openapi/live/video/start", Map.of("url", source));
            String liveId = started.path("liveId").asText("");
            String zlmStream = UniviewCloudDeviceIds.zlmStream(target.serial(), target.channelNo());
            boolean pulled = zlmClient.addDirectPullProxy(UniviewCloudSyncService.APP, zlmStream, source);
            if (!pulled) {
                stopCloud(liveId);
                throw new IllegalArgumentException("宇视云拉流失败");
            }
            String playUrl = playUrl(zlmStream);
            Session session = new Session(liveId, zlmStream, playUrl);
            sessions.put(deviceId, session);
            remember(deviceId, zlmStream, playUrl);
            log.info("[宇视云] 实况已打开 deviceId={} liveId={}", deviceId, liveId);
            return view(deviceId, session);
        }
    }

    public void stopLive(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }
        Session session;
        synchronized (sessions) {
            session = sessions.remove(deviceId.trim());
        }
        if (session == null) {
            return;
        }
        zlmClient.delStreamProxy(UniviewCloudSyncService.APP, session.zlmStream());
        forgetCloud(deviceId.trim(), session);
    }

    /**
     * 播放器都离开后停掉云端直播。本地流由流媒体按 close=true 自己关，这里不再调删除接口，避免和回调互相卡住。
     * 只有宇视云的流返回 true。
     */
    public boolean releaseWhenNoReader(String app, String zlmStream) {
        if (!UniviewCloudSyncService.APP.equals(app)) {
            return false;
        }
        String deviceId;
        try {
            deviceId = UniviewCloudDeviceIds.deviceIdFromZlmStream(zlmStream);
        } catch (IllegalArgumentException e) {
            log.info("[宇视云] 忽略无法识别的无人观看 stream={}", zlmStream);
            return true;
        }
        Session session;
        synchronized (sessions) {
            session = sessions.remove(deviceId);
        }
        if (session != null) {
            forgetCloud(deviceId, session);
        }
        return true;
    }

    private void forgetCloud(String deviceId, Session session) {
        stopCloud(session.liveId());
        streamRepository.findByDeviceIdAndType(deviceId, "main").ifPresent(stream -> {
            stream.setStatus("OFF");
            stream.setStreamUrl(UniviewCloudSyncService.STREAM_PLACEHOLDER);
            streamRepository.update(stream);
        });
        log.info("[宇视云] 实况已关闭 deviceId={}", deviceId);
    }

    private void enableLive(String serial) {
        JsonNode data = client.post("/openapi/live/config/batch/set", Map.of(
                "deviceSerialList", List.of(serial),
                "liveEnable", 1
        ));
        JsonNode list = data.path("resultList");
        if (!list.isArray() || list.isEmpty()) {
            return;
        }
        JsonNode item = list.get(0);
        int code = item.path("code").asInt(200);
        if (code != 200) {
            throw new IllegalArgumentException(UniviewCloudClient.explain(code, item.path("message").asText("")));
        }
    }

    private String liveUrl(UniviewCloudDeviceIds.Target target, String deviceName) {
        int quality = properties.qualityFor(deviceName);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceSerial", target.serial());
        body.put("channelNo", target.channelNo());
        body.put("expireTime", 3600);
        body.put("protocol", properties.getProtocol());
        body.put("quality", quality);
        body.put("type", 1);
        log.info("[宇视云] 取流 name={} serial={} channel={} quality={}",
                deviceName, target.serial(), target.channelNo(), quality);
        JsonNode data = client.post("/openapi/live/video/get", body);
        String url = data.path("url").asText("");
        if (url.isBlank()) {
            throw new IllegalArgumentException("宇视云没有返回播放地址");
        }
        return url;
    }

    private void stopCloud(String liveId) {
        if (liveId == null || liveId.isBlank()) {
            return;
        }
        try {
            client.post("/openapi/live/video/stop", Map.of("liveId", liveId));
        } catch (Exception e) {
            log.warn("[宇视云] 关闭云端直播失败 liveId={} err={}", liveId, e.getMessage());
        }
    }

    private void remember(String deviceId, String zlmStream, String playUrl) {
        try {
            DeviceStream stream = streamRepository.findByDeviceIdAndType(deviceId, "main").orElse(null);
            if (stream == null) {
                stream = new DeviceStream();
                stream.setDeviceId(deviceId);
                stream.setStreamType("main");
                stream.setStreamName(deviceId);
                stream.setSortNo(1);
                stream.setLiveEnabled(true);
                stream.setZlmApp(UniviewCloudSyncService.APP);
                stream.setZlmStream(zlmStream);
                stream.setStreamUrl(playUrl);
                stream.setStatus("ON");
                streamRepository.insert(stream);
                return;
            }
            stream.setZlmApp(UniviewCloudSyncService.APP);
            stream.setZlmStream(zlmStream);
            stream.setStreamUrl(playUrl);
            stream.setStatus("ON");
            stream.setLiveEnabled(true);
            streamRepository.update(stream);
        } catch (Exception e) {
            log.warn("[宇视云] 写入播放地址失败 deviceId={} err={}", deviceId, e.getMessage());
        }
    }

    /** 内存里的会话还在，但流媒体可能已经因无人观看把流关了。 */
    private boolean stillOnline(Session session) {
        Optional<Boolean> online = zlmClient.isMediaOnline(UniviewCloudSyncService.APP, session.zlmStream());
        return online.orElse(true);
    }

    private String playUrl(String zlmStream) {
        String base = zlmProperties.getPlayBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + UniviewCloudSyncService.APP + "/" + zlmStream + ".live.flv";
    }

    private static Map<String, Object> view(String deviceId, Session session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deviceId", deviceId);
        data.put("streamType", "main");
        data.put("streamUrl", session.playUrl());
        data.put("playUrl", session.playUrl());
        data.put("liveEnabled", true);
        data.put("transport", "uniview-cloud");
        data.put("liveId", session.liveId());
        return data;
    }

    private record Session(String liveId, String zlmStream, String playUrl) {
    }
}

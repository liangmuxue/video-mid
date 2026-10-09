package com.jizhi.videomid.uniview.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.jizhi.videomid.device.Device;
import com.jizhi.videomid.device.DeviceRepository;
import com.jizhi.videomid.device.DeviceStream;
import com.jizhi.videomid.device.DeviceStreamRepository;
import com.jizhi.videomid.media.ZlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 宇视拉流：没人看不拉；第一人观看才拉；已经在拉则直接播放。
 * 刚拉起的十几秒内不因「还没有播放器连上」而停掉。
 */
@Service
public class UniviewStreamKeeper {

    private static final Logger log = LoggerFactory.getLogger(UniviewStreamKeeper.class);
    private static final long GRACE_MS = 15_000;

    private final UniviewLapiClient lapiClient;
    private final ZlmClient zlmClient;
    private final DeviceStreamRepository streamRepository;
    private final DeviceRepository deviceRepository;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> ensuredAt = new ConcurrentHashMap<>();

    public UniviewStreamKeeper(UniviewLapiClient lapiClient,
                               ZlmClient zlmClient,
                               DeviceStreamRepository streamRepository,
                               DeviceRepository deviceRepository) {
        this.lapiClient = lapiClient;
        this.zlmClient = zlmClient;
        this.streamRepository = streamRepository;
        this.deviceRepository = deviceRepository;
    }

    /**
     * 向摄像机查询 LiveStreamURL（RTSP），并补全设备账号；不触发 ZLM 拉流。
     */
    public java.util.Optional<String> resolveRtspLiveUrl(Device device, DeviceStream stream) {
        if (device == null || device.getHost() == null || device.getHost().isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            String raw = liveStreamUrl(device, stream);
            if (raw == null || raw.isBlank()) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(withCredentials(device, raw));
        } catch (RuntimeException e) {
            log.debug("[Uniview] resolveRtspLiveUrl failed device={} err={}", device.getDeviceId(), e.getMessage());
            return java.util.Optional.empty();
        }
    }

    public DeviceStream ensure(Device device, DeviceStream stream) {
        if (!UniviewStreamIds.isPull(stream)) {
            return stream;
        }
        if (device.getHost() == null || device.getHost().isBlank()) {
            throw new IllegalStateException("设备未配置宇视地址: " + device.getDeviceId());
        }
        String key = lockKey(stream);
        synchronized (lock(key)) {
            if (online(stream)) {
                ensuredAt.put(key, System.currentTimeMillis());
                return stream;
            }
            String source = withCredentials(device, liveStreamUrl(device, stream));
            if (!zlmClient.addStreamProxy(stream.getZlmApp(), stream.getZlmStream(), source)) {
                markAccess(device, "offline", "ZLM 拉流失败");
                throw new IllegalStateException("拉流失败");
            }
            waitUntilOnline(stream);
            stream.setStatus("ON");
            streamRepository.update(stream);
            markAccess(device, "online", null);
            ensuredAt.put(key, System.currentTimeMillis());
            return stream;
        }
    }

    /** 无人观看时停掉向摄像机的拉流。刚 ensure 过的短暂空窗不关。 */
    public void stopIfIdle(DeviceStream stream) {
        if (!UniviewStreamIds.isPull(stream)) {
            return;
        }
        String key = lockKey(stream);
        if (withinGrace(key)) {
            return;
        }
        synchronized (lock(key)) {
            if (withinGrace(key)) {
                return;
            }
            boolean online = online(stream);
            if (!online && (stream.getStatus() == null || !"ON".equalsIgnoreCase(stream.getStatus()))) {
                return;
            }
            zlmClient.delStreamProxy(stream.getZlmApp(), stream.getZlmStream());
            if (!"OFF".equalsIgnoreCase(stream.getStatus())) {
                stream.setStatus("OFF");
                streamRepository.update(stream);
            }
            ensuredAt.remove(key);
            log.info("[Uniview] 无人观看，已停止拉流 {}/{}", stream.getZlmApp(), stream.getZlmStream());
        }
    }

    private boolean withinGrace(String key) {
        Long at = ensuredAt.get(key);
        return at != null && System.currentTimeMillis() - at < GRACE_MS;
    }

    private boolean online(DeviceStream stream) {
        return Boolean.TRUE.equals(zlmClient.isMediaOnline(stream.getZlmApp(), stream.getZlmStream()).orElse(false));
    }

    private String liveStreamUrl(Device device, DeviceStream stream) {
        String channel = device.getAccessChannel() == null || device.getAccessChannel().isBlank()
                ? "0" : device.getAccessChannel().trim();
        int index = stream.getStreamIndex() == null
                ? UniviewStreamIds.indexOf(stream.getStreamType())
                : stream.getStreamIndex();
        String path = "/LAPI/V1.0/Channels/" + channel + "/Media/Video/Streams/" + index + "/LiveStreamURL";
        LapiEndpoint endpoint = LapiEndpoint.from(device);
        try {
            JsonNode root = lapiClient.get(endpoint, path);
            int code = root.path("Response").path("ResponseCode").asInt(-1);
            if (code != 0) {
                markAccess(device, "offline", "LiveStreamURL 失败 " + code);
                throw new IllegalStateException("获取直播地址失败");
            }
            String url = root.path("Response").path("Data").path("URL").asText("");
            if (url.isBlank()) {
                markAccess(device, "offline", "LiveStreamURL 为空");
                throw new IllegalStateException("获取直播地址失败");
            }
            return url;
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("401")) {
                markAccess(device, "auth_failed", e.getMessage());
            } else if (!"online".equals(device.getAccessStatus())) {
                markAccess(device, "offline", e.getMessage());
            }
            throw e;
        }
    }

    private static String withCredentials(Device device, String rtspUrl) {
        if (rtspUrl.contains("@")) {
            return rtspUrl;
        }
        String username = device.getUsername() == null ? "" : device.getUsername();
        String password = device.getPassword() == null ? "" : device.getPassword();
        String rest = rtspUrl.startsWith("rtsp://") ? rtspUrl.substring("rtsp://".length()) : rtspUrl;
        return "rtsp://" + username + ":" + password + "@" + rest;
    }

    private void markAccess(Device device, String status, String error) {
        if (device.getId() == null) {
            return;
        }
        String message = error == null ? null : (error.length() > 500 ? error.substring(0, 500) : error);
        device.setAccessStatus(status);
        device.setAccessError(message);
        deviceRepository.updateAccess(device.getId(), status, message);
    }

    private void waitUntilOnline(DeviceStream stream) {
        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline) {
            if (online(stream)) {
                return;
            }
            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.info("[Uniview] ZLM 流尚未就绪，仍返回播放地址 {}/{}", stream.getZlmApp(), stream.getZlmStream());
    }

    private Object lock(String key) {
        return locks.computeIfAbsent(key, ignored -> new Object());
    }

    private static String lockKey(DeviceStream stream) {
        return stream.getZlmApp() + "/" + stream.getZlmStream();
    }
}

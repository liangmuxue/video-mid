package com.jizhi.videomid.rtsp;

import com.jizhi.videomid.device.Device;
import com.jizhi.videomid.device.DeviceStream;
import com.jizhi.videomid.device.DeviceStreamRepository;
import com.jizhi.videomid.media.ZlmClient;
import com.jizhi.videomid.uniview.live.UniviewStreamIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 直接拉流：地址由对方提供，有人播放才让 ZLM 去拉，没人看由现有的无人观看逻辑停掉。
 * 原始地址存在 pull_url，播放地址仍是本服务的 HTTP-FLV。
 */
@Service
public class RtspPullService {

    private static final Logger log = LoggerFactory.getLogger(RtspPullService.class);
    public static final String APP = "live";
    /** 刚拉起时播放器还没连上，这段时间内不因无人观看而停掉。 */
    private static final long GRACE_MS = 15_000;

    private final ZlmClient zlmClient;
    private final DeviceStreamRepository streamRepository;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> ensuredAt = new ConcurrentHashMap<>();

    public RtspPullService(ZlmClient zlmClient, DeviceStreamRepository streamRepository) {
        this.zlmClient = zlmClient;
        this.streamRepository = streamRepository;
    }

    public static String zlmStream(String deviceId) {
        String raw = deviceId == null ? "device" : deviceId.trim();
        String safe = raw.replaceAll("[^A-Za-z0-9_]", "_");
        return "rt_" + safe + "_main";
    }

    /** 保存或更新这一路。pullUrl 为空时沿用库里的地址。 */
    public void sync(Device device, String pullUrlRaw) {
        String pull = blank(pullUrlRaw);
        Optional<DeviceStream> main = streamRepository.findByDeviceIdAndType(device.getDeviceId(), "main");
        if (pull == null && main.isPresent()) {
            pull = blank(main.get().getPullUrl());
        }
        if (pull == null) {
            throw new IllegalArgumentException("请填写 RTSP 拉流地址");
        }
        if (!pull.toLowerCase(Locale.ROOT).startsWith("rtsp://")) {
            throw new IllegalArgumentException("拉流地址必须以 rtsp:// 开头");
        }
        for (DeviceStream existing : streamRepository.findByDeviceId(device.getDeviceId())) {
            if ("main".equalsIgnoreCase(existing.getStreamType())) {
                continue;
            }
            if (existing.getZlmApp() == null || existing.getZlmApp().isBlank()) {
                continue;
            }
            zlmClient.delStreamProxy(existing.getZlmApp(), existing.getZlmStream());
            streamRepository.deleteById(existing.getId());
        }
        String streamName = zlmStream(device.getDeviceId());
        String playUrl = UniviewStreamIds.playUrl(zlmClient.mediaBaseUrl(), streamName);
        if (main.isPresent()) {
            DeviceStream s = main.get();
            s.setStreamUrl(playUrl);
            s.setPullUrl(pull);
            s.setZlmApp(APP);
            s.setZlmStream(streamName);
            s.setStreamName("直接拉流");
            s.setStreamIndex(0);
            s.setSortNo(1);
            streamRepository.update(s);
        } else {
            DeviceStream s = new DeviceStream();
            s.setDeviceId(device.getDeviceId());
            s.setStreamType("main");
            s.setStreamName("直接拉流");
            s.setStreamUrl(playUrl);
            s.setPullUrl(pull);
            s.setStatus("OFF");
            s.setSortNo(1);
            s.setLiveEnabled(false);
            s.setStreamIndex(0);
            s.setZlmApp(APP);
            s.setZlmStream(streamName);
            long id = streamRepository.insert(s);
            s.setId(id);
        }
        if (streamRepository.findLiveByDeviceId(device.getDeviceId()).isEmpty()) {
            streamRepository.findByDeviceIdAndType(device.getDeviceId(), "main")
                    .ifPresent(s -> streamRepository.setLive(s.getId(), true));
        }
        log.info("[直接拉流] 已保存设备 {}", device.getDeviceId());
    }

    public DeviceStream ensure(Device device, DeviceStream stream) {
        String pull = blank(stream.getPullUrl());
        if (pull == null) {
            throw new IllegalStateException("未配置 RTSP 拉流地址");
        }
        if (stream.getZlmApp() == null || stream.getZlmApp().isBlank() || stream.getZlmStream() == null) {
            throw new IllegalStateException("拉流标识未生成");
        }
        String key = stream.getZlmApp() + "/" + stream.getZlmStream();
        synchronized (locks.computeIfAbsent(key, ignored -> new Object())) {
            ensuredAt.put(key, System.currentTimeMillis());
            if (Boolean.TRUE.equals(zlmClient.isMediaOnline(stream.getZlmApp(), stream.getZlmStream()).orElse(false))) {
                return stream;
            }
            if (!zlmClient.addDirectPullProxy(stream.getZlmApp(), stream.getZlmStream(), pull)) {
                throw new IllegalStateException("拉流失败");
            }
            waitUntilOnline(stream);
            stream.setStatus("ON");
            streamRepository.update(stream);
            log.info("[直接拉流] 已拉起 {}/{}", stream.getZlmApp(), stream.getZlmStream());
            return stream;
        }
    }

    /** 无人观看时停掉对源地址的拉流。刚 ensure 过的短暂空窗不关。 */
    public void stopIfIdle(DeviceStream stream) {
        if (stream == null || blank(stream.getPullUrl()) == null) {
            return;
        }
        if (stream.getZlmApp() == null || stream.getZlmApp().isBlank() || stream.getZlmStream() == null) {
            return;
        }
        String key = stream.getZlmApp() + "/" + stream.getZlmStream();
        if (withinGrace(key)) {
            return;
        }
        synchronized (locks.computeIfAbsent(key, ignored -> new Object())) {
            if (withinGrace(key)) {
                return;
            }
            zlmClient.delStreamProxy(stream.getZlmApp(), stream.getZlmStream());
            if (!"OFF".equalsIgnoreCase(stream.getStatus())) {
                stream.setStatus("OFF");
                streamRepository.update(stream);
            }
            ensuredAt.remove(key);
            log.info("[直接拉流] 无人观看，已停止 {}/{}", stream.getZlmApp(), stream.getZlmStream());
        }
    }

    private boolean withinGrace(String key) {
        Long at = ensuredAt.get(key);
        return at != null && System.currentTimeMillis() - at < GRACE_MS;
    }

    private void waitUntilOnline(DeviceStream stream) {
        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline) {
            if (Boolean.TRUE.equals(zlmClient.isMediaOnline(stream.getZlmApp(), stream.getZlmStream()).orElse(false))) {
                return;
            }
            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.info("[直接拉流] ZLM 流尚未就绪，仍返回播放地址 {}/{}", stream.getZlmApp(), stream.getZlmStream());
    }

    private static String blank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

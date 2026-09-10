package com.jizhi.videomid.session;

import com.jizhi.videomid.device.DeviceStream;
import com.jizhi.videomid.device.DeviceStreamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 预览地址从 MySQL 取；Redis 播放人数优先由 ZLM Hook（on_play / on_flow_report）维护。
 * /api/preview/start|stop 仍可手动加减，供未接 hook 时兜底。
 */
@Service
public class PreviewService {

    private static final Logger log = LoggerFactory.getLogger(PreviewService.class);

    private final DeviceStreamRepository streamRepository;
    private final StringRedisTemplate redis;

    public PreviewService(DeviceStreamRepository streamRepository, StringRedisTemplate redis) {
        this.streamRepository = streamRepository;
        this.redis = redis;
    }

    public Map<String, Object> start(String deviceId, String streamType) {
        String type = normalize(streamType);
        DeviceStream stream = streamRepository.findByDeviceIdAndType(deviceId, type)
                .orElseThrow(() -> new IllegalArgumentException("码流未注册: " + deviceId + "/" + type));
        if (stream.getStreamUrl() == null || stream.getStreamUrl().isBlank()) {
            throw new IllegalArgumentException("码流地址为空，请先注册 streamUrl");
        }

        // 接上 ZLM on_play 后，实际拉流会再 +1；此处不再 +1，避免关页面前端未调 stop 时双计。
        // 未配置 hook 时，人数可能不涨，可用 stop 对账或开启 hook。
        Map<String, Object> data = new HashMap<>();
        data.put("deviceId", deviceId);
        data.put("streamType", type);
        data.put("streamUrl", stream.getStreamUrl());
        data.put("playUrl", stream.getStreamUrl());
        data.put("ref", getRef(deviceId, type));
        data.put("countBy", "zlm-hook");
        return data;
    }

    public Map<String, Object> stop(String deviceId, String streamType) {
        // 正常断开由 on_flow_report 减人；此接口保留作兜底手动校正
        long ref = decrease(deviceId, normalize(streamType));
        Map<String, Object> data = new HashMap<>();
        data.put("deviceId", deviceId);
        data.put("streamType", normalize(streamType));
        data.put("ref", ref);
        data.put("stopped", ref <= 0);
        return data;
    }

    public long getRef(String deviceId, String streamType) {
        try {
            String v = redis.opsForValue().get(RedisKeys.streamRef(deviceId, normalize(streamType)));
            return v == null ? 0L : Long.parseLong(v);
        } catch (Exception e) {
            return 0L;
        }
    }

    /** ZLM on_play：有人开始拉流 */
    public long onPlayerStart(String app, String stream) {
        Optional<DeviceStream> found = findByAppStream(app, stream);
        if (found.isEmpty()) {
            log.debug("hook on_play 未匹配码流 app={} stream={}", app, stream);
            return -1L;
        }
        DeviceStream s = found.get();
        return increase(s.getDeviceId(), s.getStreamType());
    }

    /** ZLM on_flow_report 且 player=true：播放器断开 */
    public long onPlayerStop(String app, String stream) {
        Optional<DeviceStream> found = findByAppStream(app, stream);
        if (found.isEmpty()) {
            log.debug("hook on_flow_report 未匹配码流 app={} stream={}", app, stream);
            return -1L;
        }
        DeviceStream s = found.get();
        return decrease(s.getDeviceId(), s.getStreamType());
    }

    /** 无人观看：直接清零 */
    public long onNoneReader(String app, String stream) {
        Optional<DeviceStream> found = findByAppStream(app, stream);
        if (found.isEmpty()) {
            return -1L;
        }
        DeviceStream s = found.get();
        String key = RedisKeys.streamRef(s.getDeviceId(), normalize(s.getStreamType()));
        try {
            redis.opsForValue().set(key, "0");
            redis.expire(key, Duration.ofDays(1));
        } catch (Exception ignored) {
            // ignore
        }
        return 0L;
    }

    Optional<DeviceStream> findByAppStream(String app, String stream) {
        if (app == null || stream == null || app.isBlank() || stream.isBlank()) {
            return Optional.empty();
        }
        String wantApp = app.trim();
        String wantStream = stream.trim();
        for (DeviceStream s : streamRepository.findAll()) {
            AppStream as = parseAppStream(s.getStreamUrl());
            if (as != null && wantApp.equalsIgnoreCase(as.app()) && wantStream.equalsIgnoreCase(as.stream())) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    static AppStream parseAppStream(String streamUrl) {
        if (streamUrl == null || streamUrl.isBlank()) {
            return null;
        }
        try {
            String raw = streamUrl.trim();
            String lower = raw.toLowerCase(Locale.ROOT);
            // http(s)://host:port/app/stream.live.flv
            if (lower.startsWith("http://") || lower.startsWith("https://")) {
                URI u = URI.create(raw);
                String path = u.getPath() == null ? "" : u.getPath().replaceAll("^/+|/+$", "");
                if (path.toLowerCase(Locale.ROOT).endsWith(".live.flv")) {
                    path = path.substring(0, path.length() - ".live.flv".length());
                } else if (path.toLowerCase(Locale.ROOT).endsWith(".flv")) {
                    path = path.substring(0, path.length() - 4);
                }
                String[] parts = path.split("/");
                if (parts.length < 2) {
                    return null;
                }
                String stream = parts[parts.length - 1];
                StringBuilder app = new StringBuilder();
                for (int i = 0; i < parts.length - 1; i++) {
                    if (i > 0) app.append('/');
                    app.append(parts[i]);
                }
                return new AppStream(app.toString(), stream);
            }
            // rtmp(s)://host/app/stream 或 rtsp://host/app/stream
            if (lower.startsWith("rtmp") || lower.startsWith("rtsp")) {
                URI u = URI.create(raw);
                String path = u.getPath() == null ? "" : u.getPath().replaceAll("^/+|/+$", "");
                String[] parts = path.split("/");
                if (parts.length < 2) {
                    return null;
                }
                String stream = parts[parts.length - 1];
                StringBuilder app = new StringBuilder();
                for (int i = 0; i < parts.length - 1; i++) {
                    if (i > 0) app.append('/');
                    app.append(parts[i]);
                }
                return new AppStream(app.toString(), stream);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private long increase(String deviceId, String streamType) {
        String key = RedisKeys.streamRef(deviceId, normalize(streamType));
        try {
            Long ref = redis.opsForValue().increment(key);
            if (ref == null) ref = 1L;
            redis.expire(key, Duration.ofDays(1));
            log.info("播放+1 deviceId={} type={} ref={}", deviceId, streamType, ref);
            return ref;
        } catch (Exception e) {
            return 1L;
        }
    }

    private long decrease(String deviceId, String streamType) {
        String key = RedisKeys.streamRef(deviceId, normalize(streamType));
        try {
            Long v = redis.opsForValue().decrement(key);
            if (v == null || v < 0) {
                redis.opsForValue().set(key, "0");
                return 0L;
            }
            log.info("播放-1 deviceId={} type={} ref={}", deviceId, streamType, v);
            return v;
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String normalize(String streamType) {
        if (streamType == null || streamType.isBlank()) return "sub";
        return streamType.toLowerCase(Locale.ROOT);
    }

    record AppStream(String app, String stream) {}
}

package com.jizhi.videomid.session;

import com.jizhi.videomid.device.DeviceStream;
import com.jizhi.videomid.device.DeviceStreamRepository;
import com.jizhi.videomid.media.ZlmClient;
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
import java.util.OptionalInt;

/**
 * 预览地址从 MySQL 取；Redis 播放人数只由 ZLM Hook 维护
 * （on_play / on_flow_report / on_stream_none_reader，以及按 ZLM 观看数纠偏）。
 */
@Service
public class PreviewService {

    private static final Logger log = LoggerFactory.getLogger(PreviewService.class);

    private final DeviceStreamRepository streamRepository;
    private final StringRedisTemplate redis;
    private final ZlmClient zlmClient;

    public PreviewService(DeviceStreamRepository streamRepository, StringRedisTemplate redis, ZlmClient zlmClient) {
        this.streamRepository = streamRepository;
        this.redis = redis;
        this.zlmClient = zlmClient;
    }

    public Map<String, Object> start(String deviceId, String streamType) {
        String type = normalize(streamType);
        DeviceStream stream = streamRepository.findByDeviceIdAndType(deviceId, type)
                .orElseThrow(() -> new IllegalArgumentException("码流未注册: " + deviceId + "/" + type));
        if (stream.getStreamUrl() == null || stream.getStreamUrl().isBlank()) {
            throw new IllegalArgumentException("码流地址为空，请先注册 streamUrl");
        }

        // 人数只由 ZLM Hook 维护；此处只返回地址。
        Map<String, Object> data = new HashMap<>();
        data.put("deviceId", deviceId);
        data.put("streamType", type);
        data.put("streamUrl", stream.getStreamUrl());
        data.put("playUrl", stream.getStreamUrl());
        data.put("ref", getRef(deviceId, type));
        data.put("countBy", "zlm-hook");
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

    /** ZLM on_play：同一连接 id 只计一次，避免 HTTP-FLV 重试把人数刷高。 */
    public long onPlayerStart(String app, String stream, String sessionId) {
        Optional<DeviceStream> found = findByAppStream(app, stream);
        if (found.isEmpty()) {
            log.info("hook on_play 未匹配码流 app={} stream={}", app, stream);
            return -1L;
        }
        DeviceStream s = found.get();
        if (sessionId != null && !sessionId.isBlank()) {
            return addPlayerSession(s.getDeviceId(), s.getStreamType(), sessionId);
        }
        return increase(s.getDeviceId(), s.getStreamType());
    }

    /** 播放断开：优先按 ZLM 当前观看人数回写，否则按连接 id 从集合移除。 */
    public long onPlayerStop(String app, String stream, String sessionId) {
        Optional<DeviceStream> found = findByAppStream(app, stream);
        if (found.isEmpty()) {
            log.info("hook on_flow_report 未匹配码流 app={} stream={}", app, stream);
            return -1L;
        }
        DeviceStream s = found.get();
        String streamId = stripPlaySuffix(stream);
        OptionalInt readers = zlmClient.getTotalReaderCount(app, streamId);
        if (readers.isPresent()) {
            return setRef(s.getDeviceId(), s.getStreamType(), readers.getAsInt());
        }
        if (sessionId != null && !sessionId.isBlank()) {
            return removePlayerSession(s.getDeviceId(), s.getStreamType(), sessionId);
        }
        return decrease(s.getDeviceId(), s.getStreamType());
    }

    /** 无人观看：直接清零 */
    public long onNoneReader(String app, String stream) {
        Optional<DeviceStream> found = findByAppStream(app, stream);
        if (found.isEmpty()) {
            log.info("hook on_stream_none_reader 未匹配码流 app={} stream={}", app, stream);
            return -1L;
        }
        DeviceStream s = found.get();
        clearPlayerSessions(s.getDeviceId(), s.getStreamType());
        return setRef(s.getDeviceId(), s.getStreamType(), 0);
    }

    /** 按 ZLM getMediaList 把已注册码流的 Redis 人数对齐（关掉播放器后的兜底）。 */
    public void reconcileFromZlm() {
        for (DeviceStream s : streamRepository.findAll()) {
            AppStream as = parseAppStream(s.getStreamUrl());
            if (as == null) {
                continue;
            }
            OptionalInt readers = zlmClient.getTotalReaderCount(as.app(), as.stream());
            if (readers.isEmpty()) {
                continue;
            }
            long current = getRef(s.getDeviceId(), s.getStreamType());
            int actual = readers.getAsInt();
            if (current != actual) {
                setRef(s.getDeviceId(), s.getStreamType(), actual);
            }
            if (actual <= 0) {
                clearPlayerSessions(s.getDeviceId(), s.getStreamType());
            }
        }
    }

    Optional<DeviceStream> findByAppStream(String app, String stream) {
        if (app == null || stream == null || app.isBlank() || stream.isBlank()) {
            return Optional.empty();
        }
        String wantApp = app.trim();
        String wantStream = stripPlaySuffix(stream.trim());
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

    private long addPlayerSession(String deviceId, String streamType, String sessionId) {
        String pkey = RedisKeys.streamPlayers(deviceId, normalize(streamType));
        try {
            redis.opsForSet().add(pkey, sessionId);
            redis.expire(pkey, Duration.ofDays(1));
            Long n = redis.opsForSet().size(pkey);
            long ref = n == null ? 1L : n;
            return setRef(deviceId, streamType, ref);
        } catch (Exception e) {
            return increase(deviceId, streamType);
        }
    }

    private long removePlayerSession(String deviceId, String streamType, String sessionId) {
        String pkey = RedisKeys.streamPlayers(deviceId, normalize(streamType));
        try {
            redis.opsForSet().remove(pkey, sessionId);
            Long n = redis.opsForSet().size(pkey);
            long ref = n == null || n < 0 ? 0L : n;
            return setRef(deviceId, streamType, ref);
        } catch (Exception e) {
            return decrease(deviceId, streamType);
        }
    }

    private void clearPlayerSessions(String deviceId, String streamType) {
        try {
            redis.delete(RedisKeys.streamPlayers(deviceId, normalize(streamType)));
        } catch (Exception ignored) {
            // ignore
        }
    }

    private long setRef(String deviceId, String streamType, long value) {
        if (value < 0) {
            value = 0;
        }
        String key = RedisKeys.streamRef(deviceId, normalize(streamType));
        try {
            redis.opsForValue().set(key, String.valueOf(value));
            redis.expire(key, Duration.ofDays(1));
            log.info("播放人数同步 deviceId={} type={} ref={}", deviceId, streamType, value);
            return value;
        } catch (Exception e) {
            log.warn("播放人数写入 Redis 失败 deviceId={} type={}: {}", deviceId, streamType, e.getMessage());
            return value;
        }
    }

    static String stripPlaySuffix(String stream) {
        if (stream == null || stream.isBlank()) {
            return "";
        }
        String s = stream.trim();
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".live.flv")) {
            return s.substring(0, s.length() - ".live.flv".length());
        }
        if (lower.endsWith(".flv")) {
            return s.substring(0, s.length() - 4);
        }
        if (lower.endsWith("/hls.m3u8")) {
            return s.substring(0, s.length() - "/hls.m3u8".length());
        }
        if (lower.endsWith(".m3u8")) {
            return s.substring(0, s.length() - 5);
        }
        return s;
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

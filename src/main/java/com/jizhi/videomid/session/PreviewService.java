package com.jizhi.videomid.session;

import com.jizhi.videomid.device.DeviceStream;
import com.jizhi.videomid.device.DeviceStreamRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 预览：从 MySQL 取已注册的 stream_url；Redis 只做播放数量计数。
 */
@Service
public class PreviewService {

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

        String key = RedisKeys.streamRef(deviceId, type);
        Long ref = 1L;
        try {
            ref = redis.opsForValue().increment(key);
            if (ref == null) ref = 1L;
            redis.expire(key, Duration.ofDays(1));
        } catch (Exception e) {
            // Redis 不可用时仍返回地址，引用计数降级
            ref = 1L;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("deviceId", deviceId);
        data.put("streamType", type);
        data.put("streamUrl", stream.getStreamUrl());
        data.put("playUrl", stream.getStreamUrl());
        data.put("ref", ref);
        return data;
    }

    public Map<String, Object> stop(String deviceId, String streamType) {
        String type = normalize(streamType);
        String key = RedisKeys.streamRef(deviceId, type);
        long ref = 0L;
        try {
            Long v = redis.opsForValue().decrement(key);
            if (v == null || v < 0) {
                redis.opsForValue().set(key, "0");
                ref = 0L;
            } else {
                ref = v;
            }
        } catch (Exception ignored) {
            ref = 0L;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("deviceId", deviceId);
        data.put("streamType", type);
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

    private String normalize(String streamType) {
        if (streamType == null || streamType.isBlank()) return "sub";
        return streamType.toLowerCase();
    }
}

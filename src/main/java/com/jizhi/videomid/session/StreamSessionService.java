package com.jizhi.videomid.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jizhi.videomid.config.VideoMidProperties;
import com.jizhi.videomid.device.entity.StreamSessionLogEntity;
import com.jizhi.videomid.device.repository.StreamSessionLogRepository;
import com.jizhi.videomid.device.service.DeviceService;
import com.jizhi.videomid.media.ZlmClient;
import com.jizhi.videomid.sip.SipInviteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 点播会话管理：Redis 流引用计数、流生命周期控制。
 */
@Service
public class StreamSessionService {

    private static final Logger log = LoggerFactory.getLogger(StreamSessionService.class);
    private static final DateTimeFormatter GB_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final StringRedisTemplate redis;
    private final ZlmClient zlmClient;
    private final DeviceService deviceService;
    private final SipInviteService sipInviteService;
    private final VideoMidProperties props;
    private final StreamSessionLogRepository sessionLogRepository;
    private final ObjectMapper objectMapper;

    public StreamSessionService(StringRedisTemplate redis,
                                ZlmClient zlmClient,
                                DeviceService deviceService,
                                SipInviteService sipInviteService,
                                VideoMidProperties props,
                                StreamSessionLogRepository sessionLogRepository,
                                ObjectMapper objectMapper) {
        this.redis = redis;
        this.zlmClient = zlmClient;
        this.deviceService = deviceService;
        this.sipInviteService = sipInviteService;
        this.props = props;
        this.sessionLogRepository = sessionLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 开启直播预览。同一路多人观看只 INVITE 一次。
     */
    public Map<String, Object> startPreview(String deviceId, String streamType) {
        String type = normalizeStreamType(streamType);
        String channelId = deviceService.resolveChannelId(deviceId, type);
        String refKey = RedisKeys.streamRef(channelId, type);
        Long ref = redis.opsForValue().increment(refKey);
        if (ref == null) {
            ref = 1L;
        }
        redis.expire(refKey, Duration.ofSeconds(props.getLiveUrlTtlSeconds()));

        if (ref > 1) {
            String cached = redis.opsForValue().get(RedisKeys.streamUrl(channelId, type));
            if (cached != null && !cached.isBlank()) {
                Map<String, Object> urls = readUrlJson(cached);
                urls.put("reused", true);
                urls.put("ref", ref);
                urls.put("deviceId", deviceId);
                urls.put("channelId", channelId);
                urls.put("streamType", type);
                return urls;
            }
        }

        // 首次点播：openRtpServer → SDP → SIP INVITE Play
        String streamId = channelId;
        int rtpPort = zlmClient.openRtpServer(streamId, 0);
        String sessionId = UUID.randomUUID().toString().replace("-", "");

        saveMeta(channelId, type, streamId, rtpPort, sessionId, "LIVE", null, null);
        writeSessionLog(sessionId, channelId, type, "LIVE", null, null, streamId, rtpPort);

        sipInviteService.invitePlay(channelId, streamId, rtpPort);

        waitStreamReady(streamId);

        String flv = zlmClient.buildFlvUrl(streamId);
        String hls = zlmClient.buildHlsUrl(streamId);
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("deviceId", deviceId);
        result.put("channelId", channelId);
        result.put("streamType", type);
        result.put("streamId", streamId);
        result.put("rtpPort", rtpPort);
        result.put("playUrl", flv);
        result.put("playUrlFlv", flv);
        result.put("playUrlHls", hls);
        result.put("reused", false);
        result.put("ref", ref);

        cacheUrls(channelId, type, result, props.getLiveUrlTtlSeconds());
        updateSessionLogPlaying(sessionId, flv, hls);
        return result;
    }

    public Map<String, Object> stopPreview(String deviceId, String streamType) {
        String type = normalizeStreamType(streamType);
        String channelId = deviceService.resolveChannelId(deviceId, type);
        String refKey = RedisKeys.streamRef(channelId, type);
        Long ref = redis.opsForValue().decrement(refKey);
        if (ref == null || ref < 0) {
            redis.opsForValue().set(refKey, "0");
            ref = 0L;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("deviceId", deviceId);
        result.put("channelId", channelId);
        result.put("streamType", type);
        result.put("ref", ref);

        if (ref == 0) {
            String task = redis.opsForValue().get(RedisKeys.streamTask(channelId));
            if (task == null || task.isBlank()) {
                teardownStream(channelId, type);
                result.put("stopped", true);
            } else {
                result.put("stopped", false);
                result.put("reason", "stream:task occupied");
            }
        } else {
            result.put("stopped", false);
        }
        return result;
    }

    /**
     * 按时间段回放（Playback INVITE）。
     */
    public Map<String, Object> startPlayback(String deviceId, String startTime, String endTime, String streamType) {
        String type = normalizeStreamType(streamType);
        String channelId = deviceService.resolveChannelId(deviceId, type);
        String streamId = "pb_" + channelId + "_" + System.currentTimeMillis();
        int rtpPort = zlmClient.openRtpServer(streamId, 0);
        String sessionId = UUID.randomUUID().toString().replace("-", "");

        LocalDateTime start = parseTime(startTime);
        LocalDateTime end = parseTime(endTime);

        saveMeta(channelId, type, streamId, rtpPort, sessionId, "PLAYBACK", startTime, endTime);
        writeSessionLog(sessionId, channelId, type, "PLAYBACK", start, end, streamId, rtpPort);

        sipInviteService.invitePlayback(channelId, streamId, rtpPort, startTime, endTime);
        waitStreamReady(streamId);

        String flv = zlmClient.buildFlvUrl(streamId);
        String hls = zlmClient.buildHlsUrl(streamId);
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("deviceId", deviceId);
        result.put("channelId", channelId);
        result.put("streamType", type);
        result.put("streamId", streamId);
        result.put("rtpPort", rtpPort);
        result.put("playUrl", flv);
        result.put("playUrlFlv", flv);
        result.put("playUrlHls", hls);
        result.put("startTime", startTime);
        result.put("endTime", endTime);
        result.put("ttlSeconds", props.getPlaybackUrlTtlSeconds());

        // 回放地址短时缓存
        try {
            redis.opsForValue().set(
                    RedisKeys.streamUrl(streamId, type),
                    objectMapper.writeValueAsString(result),
                    Duration.ofSeconds(props.getPlaybackUrlTtlSeconds())
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        updateSessionLogPlaying(sessionId, flv, hls);
        return result;
    }

    public void onStreamReady(String streamId) {
        log.info("Stream ready: {}", streamId);
        redis.opsForValue().set(RedisKeys.streamReady(streamId), "1", Duration.ofMinutes(10));
    }

    /**
     * @return 是否建议 ZLM 关闭该流
     */
    public boolean onNoneReader(String streamId) {
        // 根据 meta 反查 channel，检查引用计数与分析任务
        String metaJson = findMetaByStreamId(streamId);
        if (metaJson == null) {
            return true;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = objectMapper.readValue(metaJson, Map.class);
            String channelId = String.valueOf(meta.get("channelId"));
            String type = String.valueOf(meta.getOrDefault("streamType", "sub"));
            String refStr = redis.opsForValue().get(RedisKeys.streamRef(channelId, type));
            long ref = refStr == null ? 0 : Long.parseLong(refStr);
            String task = redis.opsForValue().get(RedisKeys.streamTask(channelId));
            if (ref <= 0 && (task == null || task.isBlank())) {
                teardownStream(channelId, type);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("onNoneReader parse meta failed: {}", e.getMessage());
            return false;
        }
    }

    private void teardownStream(String channelId, String type) {
        String metaJson = redis.opsForValue().get(RedisKeys.streamMeta(channelId, type));
        String streamId = channelId;
        if (metaJson != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> meta = objectMapper.readValue(metaJson, Map.class);
                streamId = String.valueOf(meta.getOrDefault("streamId", channelId));
                String sessionId = String.valueOf(meta.getOrDefault("sessionId", ""));
                if (!sessionId.isBlank()) {
                    sessionLogRepository.findBySessionId(sessionId).ifPresent(logEntity -> {
                        logEntity.setStatus("STOPPED");
                        logEntity.setStoppedAt(LocalDateTime.now());
                        sessionLogRepository.save(logEntity);
                    });
                }
            } catch (Exception ignored) {
            }
        }
        try {
            sipInviteService.sendBye(channelId);
        } catch (Exception e) {
            log.warn("SIP BYE failed for {}: {}", channelId, e.getMessage());
        }
        zlmClient.closeRtpServer(streamId);
        redis.delete(RedisKeys.streamUrl(channelId, type));
        redis.delete(RedisKeys.streamMeta(channelId, type));
        redis.delete(RedisKeys.streamRef(channelId, type));
        redis.delete(RedisKeys.streamReady(streamId));
        log.info("Teardown stream channelId={} streamId={}", channelId, streamId);
    }

    private void waitStreamReady(String streamId) {
        long deadline = System.currentTimeMillis() + props.getStreamReadyTimeoutMs();
        while (System.currentTimeMillis() < deadline) {
            String ready = redis.opsForValue().get(RedisKeys.streamReady(streamId));
            if ("1".equals(ready) || zlmClient.isMediaOnline(streamId)) {
                return;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.warn("Wait stream ready timeout: {}, still return playUrl for client retry", streamId);
    }

    private void saveMeta(String channelId, String type, String streamId, int rtpPort,
                          String sessionId, String playType, String start, String end) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("channelId", channelId);
        meta.put("streamType", type);
        meta.put("streamId", streamId);
        meta.put("rtpPort", rtpPort);
        meta.put("sessionId", sessionId);
        meta.put("playType", playType);
        meta.put("startTime", start);
        meta.put("endTime", end);
        try {
            redis.opsForValue().set(
                    RedisKeys.streamMeta(channelId, type),
                    objectMapper.writeValueAsString(meta),
                    Duration.ofSeconds(props.getLiveUrlTtlSeconds())
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private void cacheUrls(String channelId, String type, Map<String, Object> urls, long ttlSeconds) {
        try {
            redis.opsForValue().set(
                    RedisKeys.streamUrl(channelId, type),
                    objectMapper.writeValueAsString(urls),
                    Duration.ofSeconds(ttlSeconds)
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> readUrlJson(String json) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            return map;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String findMetaByStreamId(String streamId) {
        // 简化：用 streamId 作为 channel 或 pb_ 前缀扫描 meta 不现实，存一份反向索引
        String reverse = redis.opsForValue().get("stream:id:" + streamId);
        if (reverse != null) {
            return redis.opsForValue().get(reverse);
        }
        return null;
    }

    private void writeSessionLog(String sessionId, String channelId, String type, String playType,
                                 LocalDateTime start, LocalDateTime end, String streamId, int rtpPort) {
        StreamSessionLogEntity logEntity = new StreamSessionLogEntity();
        logEntity.setSessionId(sessionId);
        logEntity.setDeviceId(channelId);
        logEntity.setStreamType(type);
        logEntity.setPlayType(playType);
        logEntity.setStartTime(start);
        logEntity.setEndTime(end);
        logEntity.setStreamId(streamId);
        logEntity.setRtpPort(rtpPort);
        logEntity.setStatus("STARTING");
        sessionLogRepository.save(logEntity);
        redis.opsForValue().set("stream:id:" + streamId, RedisKeys.streamMeta(channelId, type),
                Duration.ofSeconds(props.getLiveUrlTtlSeconds()));
    }

    private void updateSessionLogPlaying(String sessionId, String flv, String hls) {
        sessionLogRepository.findBySessionId(sessionId).ifPresent(e -> {
            e.setStatus("PLAYING");
            e.setPlayUrlFlv(flv);
            e.setPlayUrlHls(hls);
            sessionLogRepository.save(e);
        });
    }

    private String normalizeStreamType(String streamType) {
        if (streamType == null || streamType.isBlank()) {
            return props.getDefaultStreamType() == null ? "sub" : props.getDefaultStreamType();
        }
        return streamType.toLowerCase();
    }

    private LocalDateTime parseTime(String time) {
        if (time == null || time.isBlank()) {
            return null;
        }
        String t = time.trim().replace(" ", "T");
        if (t.length() == 19) {
            return LocalDateTime.parse(t, GB_TIME);
        }
        return LocalDateTime.parse(t);
    }
}

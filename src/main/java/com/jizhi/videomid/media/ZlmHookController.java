package com.jizhi.videomid.media;

import com.jizhi.videomid.session.RedisKeys;
import com.jizhi.videomid.session.StreamSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/**
 * 接收 ZLMediaKit Hook 回调：on_stream_changed、on_stream_none_reader。
 */
@RestController
@RequestMapping("/index/hook")
public class ZlmHookController {

    private static final Logger log = LoggerFactory.getLogger(ZlmHookController.class);

    private final StringRedisTemplate redis;
    private final StreamSessionService streamSessionService;
    private final ZlmClient zlmClient;

    public ZlmHookController(StringRedisTemplate redis,
                             StreamSessionService streamSessionService,
                             ZlmClient zlmClient) {
        this.redis = redis;
        this.streamSessionService = streamSessionService;
        this.zlmClient = zlmClient;
    }

    @PostMapping("/on_stream_changed")
    public Map<String, Object> onStreamChanged(@RequestBody Map<String, Object> body) {
        log.info("ZLM on_stream_changed: {}", body);
        boolean regist = Boolean.parseBoolean(String.valueOf(body.getOrDefault("regist", false)));
        String app = String.valueOf(body.getOrDefault("app", ""));
        String stream = String.valueOf(body.getOrDefault("stream", ""));
        if (regist && zlmClient.getApp().equals(app) && stream != null && !stream.isBlank()) {
            redis.opsForValue().set(RedisKeys.streamReady(stream), "1", Duration.ofMinutes(10));
            streamSessionService.onStreamReady(stream);
        }
        return Map.of("code", 0, "msg", "success");
    }

    @PostMapping("/on_stream_none_reader")
    public Map<String, Object> onStreamNoneReader(@RequestBody Map<String, Object> body) {
        log.info("ZLM on_stream_none_reader: {}", body);
        String stream = String.valueOf(body.getOrDefault("stream", ""));
        // 无人观看时由 session 决定是否 BYE（需检查 stream:ref 与 stream:task）
        boolean close = streamSessionService.onNoneReader(stream);
        return Map.of("code", 0, "close", close);
    }

    @PostMapping("/on_play")
    public Map<String, Object> onPlay(@RequestBody Map<String, Object> body) {
        return Map.of("code", 0, "msg", "success");
    }

    @PostMapping("/on_publish")
    public Map<String, Object> onPublish(@RequestBody Map<String, Object> body) {
        return Map.of("code", 0, "msg", "success");
    }

    @PostMapping("/on_server_started")
    public Map<String, Object> onServerStarted(@RequestBody(required = false) Map<String, Object> body) {
        log.info("ZLM server started hook");
        return Map.of("code", 0, "msg", "success");
    }
}

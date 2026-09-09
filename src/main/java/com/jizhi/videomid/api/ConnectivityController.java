package com.jizhi.videomid.api;

import com.jizhi.videomid.media.ZlmClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 基础连通性检查：MySQL / Redis / ZLM。无业务逻辑。
 */
@RestController
public class ConnectivityController {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redis;
    private final ZlmClient zlmClient;

    public ConnectivityController(JdbcTemplate jdbcTemplate,
                                  StringRedisTemplate redis,
                                  ZlmClient zlmClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.redis = redis;
        this.zlmClient = zlmClient;
    }

    @GetMapping("/health/deps")
    public Map<String, Object> deps() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mysql", checkMysql());
        result.put("redis", checkRedis());
        result.put("zlm", checkZlm());
        boolean ok = "UP".equals(result.get("mysql"))
                && "UP".equals(result.get("redis"))
                && "UP".equals(result.get("zlm"));
        result.put("status", ok ? "UP" : "DOWN");
        return result;
    }

    private String checkMysql() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return "UP";
        } catch (Exception e) {
            return "DOWN: " + e.getMessage();
        }
    }

    private String checkRedis() {
        try {
            redis.opsForValue().set("video-mid:ping", "1");
            String v = redis.opsForValue().get("video-mid:ping");
            return "1".equals(v) ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN: " + e.getMessage();
        }
    }

    private String checkZlm() {
        try {
            return zlmClient.ping() ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN: " + e.getMessage();
        }
    }
}

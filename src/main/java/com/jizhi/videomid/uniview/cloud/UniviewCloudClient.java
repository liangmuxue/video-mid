package com.jizhi.videomid.uniview.cloud;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/** 宇视云 HTTP。accessToken 过期或失效时换一次再重试。 */
@Component
public class UniviewCloudClient {

    private static final Logger log = LoggerFactory.getLogger(UniviewCloudClient.class);
    private static final String TOKEN_PATH = "/openapi/user/app/token/get";

    private final UniviewCloudProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final Object tokenLock = new Object();
    private String accessToken = "";
    private long expireAtMillis;

    public UniviewCloudClient(UniviewCloudProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8000);
        factory.setReadTimeout(25000);
        this.restTemplate = new RestTemplate(factory);
    }

    public void requireConfigured() {
        if (!properties.configured()) {
            throw new IllegalArgumentException("未配置宇视云 appId 或 secretKey");
        }
    }

    public JsonNode post(String path, Map<String, ?> body) {
        requireConfigured();
        JsonNode root = exchange(path, body, true);
        int code = root.path("code").asInt(-1);
        if (code != 200) {
            throw new IllegalArgumentException(explain(code, root.path("message").asText("")));
        }
        JsonNode data = root.get("data");
        return data == null || data.isNull() ? objectMapper.createObjectNode() : data;
    }

    private final Object paceLock = new Object();
    private long nextRequestAt;

    private JsonNode exchange(String path, Map<String, ?> body, boolean allowRefresh) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", token());
        HttpEntity<Map<String, ?>> entity = new HttpEntity<>(body == null ? Map.of() : body, headers);
        String url = trimSlash(properties.getBaseUrl()) + path;
        for (int attempt = 0; attempt < 6; attempt++) {
            pace();
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
                JsonNode root = read(response.getBody());
                int code = root.path("code").asInt(-1);
                if (allowRefresh && (code == 1001 || code == 1002)) {
                    clearToken();
                    return exchange(path, body, false);
                }
                return root;
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (HttpStatusCodeException e) {
                if (e.getStatusCode().value() == 429 && attempt < 5) {
                    long wait = 3_000L * (attempt + 1);
                    log.warn("[宇视云] 请求过快，{}ms 后重试 path={}", wait, path);
                    hold(wait);
                    continue;
                }
                log.warn("[宇视云] 请求失败 path={} err={}", path, e.getMessage());
                throw new IllegalArgumentException("宇视云请求失败");
            } catch (Exception e) {
                log.warn("[宇视云] 请求失败 path={} err={}", path, e.getMessage());
                throw new IllegalArgumentException("宇视云请求失败");
            }
        }
        throw new IllegalArgumentException("宇视云请求失败");
    }

    /** 通道查询太密会收到 HTTP 429，请求之间留一点间隔。 */
    private void pace() {
        synchronized (paceLock) {
            long wait = nextRequestAt - System.currentTimeMillis();
            if (wait > 0) {
                sleep(wait);
            }
            nextRequestAt = System.currentTimeMillis() + 350L;
        }
    }

    private void hold(long millis) {
        synchronized (paceLock) {
            nextRequestAt = Math.max(nextRequestAt, System.currentTimeMillis() + millis);
        }
        sleep(millis);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("宇视云请求失败");
        }
    }

    private String token() {
        synchronized (tokenLock) {
            if (!accessToken.isBlank() && System.currentTimeMillis() < expireAtMillis - 60_000L) {
                return accessToken;
            }
            Map<String, String> body = new LinkedHashMap<>();
            body.put("appId", properties.getAppId().trim());
            body.put("secretKey", properties.getSecretKey().trim());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String url = trimSlash(properties.getBaseUrl()) + TOKEN_PATH;
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(
                        url, new HttpEntity<>(body, headers), String.class);
                JsonNode root = read(response.getBody());
                int code = root.path("code").asInt(-1);
                if (code != 200) {
                    throw new IllegalArgumentException(explain(code, root.path("message").asText("")));
                }
                JsonNode data = root.path("data");
                String next = data.path("accessToken").asText("");
                if (next.isBlank()) {
                    throw new IllegalArgumentException("宇视云没有返回 accessToken");
                }
                long expire = data.path("expireTime").asLong(0L);
                accessToken = next;
                expireAtMillis = expire > 0L ? expire * 1000L : System.currentTimeMillis() + 6L * 24 * 3600_000L;
                return accessToken;
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                log.warn("[宇视云] 获取 accessToken 失败 err={}", e.getMessage());
                throw new IllegalArgumentException("宇视云鉴权失败");
            }
        }
    }

    private void clearToken() {
        synchronized (tokenLock) {
            accessToken = "";
            expireAtMillis = 0L;
        }
    }

    private JsonNode read(String body) throws Exception {
        if (body == null || body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(body);
    }

    static String explain(int code, String message) {
        String text = switch (code) {
            case 1001, 1002 -> "宇视云登录已失效";
            case 1003 -> "宇视云参数不正确";
            case 1006 -> "摄像机不支持这个功能";
            case 1009 -> "宇视云上没有这台设备";
            case 1012 -> "宇视云账号没有这项权限，请在开放平台给应用开通实况";
            case 1018 -> "摄像机不在线";
            case 1019 -> "通道不在线";
            case 2815 -> "宇视云还没有打开这台摄像机的直播";
            default -> "";
        };
        if (!text.isEmpty()) {
            return text;
        }
        if (message == null || message.isBlank()) {
            return "宇视云返回 " + code;
        }
        return "宇视云返回 " + code + " " + message;
    }

    private static String trimSlash(String base) {
        if (base == null || base.isBlank()) {
            return "https://ezcloud.uniview.com";
        }
        String value = base.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}

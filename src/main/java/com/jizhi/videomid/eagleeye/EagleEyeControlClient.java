package com.jizhi.videomid.eagleeye;

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
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 调用鹰眼子系统的控制命令。地址为空时直接失败，不发请求。 */
@Component
public class EagleEyeControlClient {

    private static final Logger log = LoggerFactory.getLogger(EagleEyeControlClient.class);

    private final EagleEyeProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public EagleEyeControlClient(EagleEyeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(factory);
    }

    public void send(String deviceId, String command, Map<String, Object> params) {
        if (!properties.ready()) {
            throw new IllegalArgumentException("鹰眼子系统地址未配置");
        }
        String terminalId = properties.terminalIdOf(deviceId);
        Map<String, Object> body = new LinkedHashMap<>();
        String requestId = "req-" + UUID.randomUUID();
        body.put("requestId", requestId);
        body.put("terminalId", terminalId);
        body.put("command", command);
        if (params != null && !params.isEmpty()) {
            body.put("params", params);
        }
        body.put("timestamp", System.currentTimeMillis());
        String url = join(properties.getBaseUrl(), properties.getControlPath());
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            JsonNode node = read(response.getBody());
            int code = node.path("code").asInt(-1);
            if (code != 0) {
                String message = node.path("message").asText("鹰眼子系统执行失败");
                throw new IllegalArgumentException(message);
            }
            log.info("[鹰眼] 控制完成 deviceId={} terminalId={} command={} requestId={}",
                    deviceId, terminalId, command, requestId);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("[鹰眼] 控制失败 url={} command={} err={}", url, command, e.getMessage());
            throw new IllegalArgumentException("鹰眼子系统执行失败");
        }
    }

    private JsonNode read(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("鹰眼子系统没有返回结果");
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalArgumentException("鹰眼子系统返回无法解析");
        }
    }

    private static String join(String baseUrl, String path) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String suffix = path == null || path.isBlank() ? "/api/control" : path.trim();
        if (!suffix.startsWith("/")) {
            suffix = "/" + suffix;
        }
        return base + suffix;
    }
}

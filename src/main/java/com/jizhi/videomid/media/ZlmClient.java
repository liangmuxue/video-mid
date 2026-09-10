package com.jizhi.videomid.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jizhi.videomid.config.ZlmProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.OptionalInt;

/**
 * ZLMediaKit HTTP API 客户端。
 * ZLM 在 Docker 内监听 80，宿主机映射 8080:80，本服务在宿主机上请求 http://127.0.0.1:8080。
 */
@Component
public class ZlmClient {

    private static final Logger log = LoggerFactory.getLogger(ZlmClient.class);

    private final ZlmProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ZlmClient(ZlmProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        this.restTemplate = new RestTemplate(factory);
    }

    @PostConstruct
    void logStartupPing() {
        boolean ok = ping();
        log.info("[本服务→ZLM] 启动探测 getServerConfig {} baseUrl={}",
                ok ? "成功" : "失败", baseUrl());
    }

    /** 调用 getServerConfig，用于验证能连上 ZLM。 */
    public JsonNode getServerConfig() {
        String url = api("/index/api/getServerConfig").toUriString();
        log.info("[本服务→ZLM] 请求 getServerConfig {}", maskSecret(url));
        try {
            String body = restTemplate.getForObject(url, String.class);
            JsonNode resp = objectMapper.readTree(body);
            int code = resp == null ? -1 : resp.path("code").asInt(-1);
            String msg = resp == null ? "empty" : resp.path("msg").asText(resp.path("message").asText(""));
            if (code == 0) {
                log.info("[本服务→ZLM] getServerConfig 成功 code={} msg={}", code, msg);
            } else {
                log.warn("[本服务→ZLM] getServerConfig 失败 code={} msg={} body={}", code, msg, body);
            }
            return resp;
        } catch (Exception e) {
            log.warn("[本服务→ZLM] getServerConfig 失败 url={} error={}", maskSecret(url), e.getMessage());
            throw new IllegalStateException("ZLM unreachable: " + e.getMessage(), e);
        }
    }

    public boolean ping() {
        try {
            JsonNode resp = getServerConfig();
            return resp != null && resp.path("code").asInt(-1) == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 查询某路流当前观看人数（各协议 totalReaderCount 取最大）。
     * 流不存在视为 0；接口失败返回 empty。
     */
    public OptionalInt getTotalReaderCount(String app, String stream) {
        if (app == null || stream == null || app.isBlank() || stream.isBlank()) {
            return OptionalInt.empty();
        }
        String url = api("/index/api/getMediaList")
                .queryParam("app", app.trim())
                .queryParam("stream", stream.trim())
                .toUriString();
        log.info("[本服务→ZLM] 请求 getMediaList app={} stream={} {}", app, stream, maskSecret(url));
        try {
            String body = restTemplate.getForObject(url, String.class);
            JsonNode resp = objectMapper.readTree(body);
            if (resp == null || resp.path("code").asInt(-1) != 0) {
                int code = resp == null ? -1 : resp.path("code").asInt(-1);
                String msg = resp == null ? "empty" : resp.path("msg").asText("");
                log.warn("[本服务→ZLM] getMediaList 失败 app={} stream={} code={} msg={} body={}",
                        app, stream, code, msg, body);
                return OptionalInt.empty();
            }
            JsonNode data = resp.path("data");
            if (!data.isArray() || data.isEmpty()) {
                log.info("[本服务→ZLM] getMediaList 成功 app={} stream={} 流不存在或无人观看 readers=0", app, stream);
                return OptionalInt.of(0);
            }
            int max = 0;
            for (JsonNode n : data) {
                int total = n.path("totalReaderCount").asInt(-1);
                if (total < 0) {
                    total = n.path("readerCount").asInt(0);
                }
                max = Math.max(max, total);
            }
            log.info("[本服务→ZLM] getMediaList 成功 app={} stream={} readers={}", app, stream, max);
            return OptionalInt.of(max);
        } catch (Exception e) {
            log.warn("[本服务→ZLM] getMediaList 失败 app={} stream={} error={}", app, stream, e.getMessage());
            return OptionalInt.empty();
        }
    }

    private UriComponentsBuilder api(String path) {
        return UriComponentsBuilder
                .fromHttpUrl(baseUrl() + path)
                .queryParam("secret", props.getSecret());
    }

    private String baseUrl() {
        String base = props.getBaseUrl();
        if (base == null || base.isBlank()) {
            return "http://127.0.0.1:8080";
        }
        String b = base.trim();
        return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
    }

    static String maskSecret(String url) {
        if (url == null) {
            return "";
        }
        return url.replaceAll("([?&]secret=)[^&]*", "$1***");
    }
}

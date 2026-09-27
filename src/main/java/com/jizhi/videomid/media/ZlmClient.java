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

import java.util.Optional;
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
    private final RestTemplate proxyRestTemplate;
    private final ObjectMapper objectMapper;

    public ZlmClient(ZlmProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        this.restTemplate = new RestTemplate(factory);
        SimpleClientHttpRequestFactory proxyFactory = new SimpleClientHttpRequestFactory();
        proxyFactory.setConnectTimeout(3000);
        proxyFactory.setReadTimeout(20000);
        this.proxyRestTemplate = new RestTemplate(proxyFactory);
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

    /**
     * 判断某路流是否仍在推流（ZLM mediaList 有条目即视为在线）。
     * 接口失败返回 empty，避免误判为离线。
     */
    public Optional<Boolean> isMediaOnline(String app, String stream) {
        if (app == null || stream == null || app.isBlank() || stream.isBlank()) {
            return Optional.empty();
        }
        String url = api("/index/api/getMediaList")
                .queryParam("app", app.trim())
                .queryParam("stream", stream.trim())
                .toUriString();
        try {
            String body = restTemplate.getForObject(url, String.class);
            JsonNode resp = objectMapper.readTree(body);
            if (resp == null || resp.path("code").asInt(-1) != 0) {
                return Optional.empty();
            }
            JsonNode data = resp.path("data");
            boolean online = data.isArray() && !data.isEmpty();
            return Optional.of(online);
        } catch (Exception e) {
            log.warn("[本服务→ZLM] isMediaOnline 失败 app={} stream={} error={}", app, stream, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 让 ZLM 拉一路外部地址并转成可播放流。流已存在视为成功。
     * 使用独立超时，避免拉流握手拖住 getMediaList 的短超时客户端。
     */
    public boolean addStreamProxy(String app, String stream, String sourceUrl) {
        if (app == null || app.isBlank() || stream == null || stream.isBlank()
                || sourceUrl == null || sourceUrl.isBlank()) {
            return false;
        }
        String url = api("/index/api/addStreamProxy")
                .queryParam("vhost", "__defaultVhost__")
                .queryParam("app", app.trim())
                .queryParam("stream", stream.trim())
                .queryParam("url", sourceUrl.trim())
                .queryParam("retry_count", -1)
                .queryParam("rtp_type", 0)
                .queryParam("timeout_sec", 15)
                .queryParam("enable_hls", 1)
                .queryParam("enable_rtmp", 1)
                .toUriString();
        log.info("[本服务→ZLM] addStreamProxy app={} stream={}", app, stream);
        try {
            String body = proxyRestTemplate.getForObject(url, String.class);
            JsonNode resp = objectMapper.readTree(body);
            int code = resp == null ? -1 : resp.path("code").asInt(-1);
            String msg = resp == null ? "" : resp.path("msg").asText("");
            if (code == 0 || msg.toLowerCase().contains("exist")) {
                log.info("[本服务→ZLM] addStreamProxy 成功 app={} stream={} code={} msg={}", app, stream, code, msg);
                return true;
            }
            log.warn("[本服务→ZLM] addStreamProxy 失败 app={} stream={} code={} msg={}", app, stream, code, msg);
            return false;
        } catch (Exception e) {
            log.warn("[本服务→ZLM] addStreamProxy 异常 app={} stream={} error={}", app, stream, e.getMessage());
            return false;
        }
    }

    /** 回放拉流：不无限重连，避免录像结束后一直重试。 */
    public boolean addPlaybackProxy(String app, String stream, String sourceUrl) {
        if (app == null || app.isBlank() || stream == null || stream.isBlank()
                || sourceUrl == null || sourceUrl.isBlank()) {
            return false;
        }
        String url = api("/index/api/addStreamProxy")
                .queryParam("vhost", "__defaultVhost__")
                .queryParam("app", app.trim())
                .queryParam("stream", stream.trim())
                .queryParam("url", sourceUrl.trim())
                .queryParam("retry_count", 0)
                .queryParam("rtp_type", 0)
                .queryParam("timeout_sec", 15)
                .queryParam("enable_hls", 0)
                .queryParam("enable_rtmp", 1)
                .queryParam("enable_mp4", 0)
                .toUriString();
        log.info("[本服务→ZLM] addPlaybackProxy app={} stream={}", app, stream);
        try {
            String body = proxyRestTemplate.getForObject(url, String.class);
            JsonNode resp = objectMapper.readTree(body);
            int code = resp == null ? -1 : resp.path("code").asInt(-1);
            String msg = resp == null ? "" : resp.path("msg").asText("");
            if (code == 0 || msg.toLowerCase().contains("exist")) {
                return true;
            }
            log.warn("[本服务→ZLM] addPlaybackProxy 失败 app={} stream={} code={} msg={}", app, stream, code, msg);
            return false;
        } catch (Exception e) {
            log.warn("[本服务→ZLM] addPlaybackProxy 异常 app={} stream={} error={}", app, stream, e.getMessage());
            return false;
        }
    }

    /** 停止 addStreamProxy 建立的拉流。代理已不存在视为成功。 */
    public boolean delStreamProxy(String app, String stream) {
        if (app == null || app.isBlank() || stream == null || stream.isBlank()) {
            return false;
        }
        String key = "__defaultVhost__/" + app.trim() + "/" + stream.trim();
        String url = api("/index/api/delStreamProxy")
                .queryParam("key", key)
                .toUriString();
        log.info("[本服务→ZLM] delStreamProxy app={} stream={}", app, stream);
        try {
            String body = proxyRestTemplate.getForObject(url, String.class);
            JsonNode resp = objectMapper.readTree(body);
            int code = resp == null ? -1 : resp.path("code").asInt(-1);
            String msg = resp == null ? "" : resp.path("msg").asText("");
            if (code == 0 || msg.toLowerCase().contains("not found") || msg.toLowerCase().contains("no such")) {
                log.info("[本服务→ZLM] delStreamProxy 成功 app={} stream={} code={} msg={}", app, stream, code, msg);
                return true;
            }
            log.warn("[本服务→ZLM] delStreamProxy 失败 app={} stream={} code={} msg={}", app, stream, code, msg);
            return false;
        } catch (Exception e) {
            log.warn("[本服务→ZLM] delStreamProxy 异常 app={} stream={} error={}", app, stream, e.getMessage());
            return false;
        }
    }

    /** 国标 live：在 ZLM 开启 RTP 接收（GB28181 PS 流） */
    public boolean openRtpServer(int port, String streamId, String app) {
        if (port <= 0 || streamId == null || streamId.isBlank()) {
            return false;
        }
        String rtpApp = (app == null || app.isBlank()) ? "rtp" : app.trim();
        String url = api("/index/api/openRtpServer")
                .queryParam("port", port)
                .queryParam("stream_id", streamId.trim())
                .queryParam("app", rtpApp)
                .toUriString();
        log.info("[本服务→ZLM] openRtpServer port={} streamId={} app={}", port, streamId, rtpApp);
        try {
            String body = restTemplate.getForObject(url, String.class);
            JsonNode resp = objectMapper.readTree(body);
            int code = resp == null ? -1 : resp.path("code").asInt(-1);
            if (code == 0) {
                return true;
            }
            log.warn("[GB28181-live] openRtpServer 失败 code={} body={}", code, body);
            return false;
        } catch (Exception e) {
            log.warn("[GB28181-live] openRtpServer 异常 port={} err={}", port, e.getMessage());
            return false;
        }
    }

    public boolean closeRtpServer(String streamId, String app) {
        if (streamId == null || streamId.isBlank()) {
            return false;
        }
        String rtpApp = (app == null || app.isBlank()) ? "rtp" : app.trim();
        String url = api("/index/api/closeRtpServer")
                .queryParam("stream_id", streamId.trim())
                .queryParam("app", rtpApp)
                .toUriString();
        try {
            String body = restTemplate.getForObject(url, String.class);
            JsonNode resp = objectMapper.readTree(body);
            return resp != null && resp.path("code").asInt(-1) == 0;
        } catch (Exception e) {
            log.warn("[GB28181-live] closeRtpServer 异常 streamId={} err={}", streamId, e.getMessage());
            return false;
        }
    }

    private UriComponentsBuilder api(String path) {
        return UriComponentsBuilder
                .fromHttpUrl(baseUrl() + path)
                .queryParam("secret", props.getSecret());
    }

    public String mediaBaseUrl() {
        return baseUrl();
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

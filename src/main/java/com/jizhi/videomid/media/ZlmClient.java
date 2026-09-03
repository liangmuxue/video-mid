package com.jizhi.videomid.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jizhi.videomid.config.ZlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * ZLMediaKit HTTP API 客户端。
 * Java 不处理视频媒体字节，仅调用 openRtpServer / closeRtpServer 等接口。
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
        this.restTemplate = new RestTemplate();
    }

    /**
     * 开启 RTP 接收端口，返回实际 UDP 端口。
     *
     * @param streamId 流 ID（通常用通道国标编码）
     * @param tcpMode  0=UDP, 1=TCP被动, 2=TCP主动
     */
    public int openRtpServer(String streamId, int tcpMode) {
        Map<String, String> params = new HashMap<>();
        params.put("secret", props.getSecret());
        params.put("port", "0");
        params.put("tcp_mode", String.valueOf(tcpMode));
        params.put("stream_id", streamId);
        JsonNode resp = get("/index/api/openRtpServer", params);
        if (resp == null || resp.path("code").asInt(-1) != 0) {
            throw new IllegalStateException("ZLM openRtpServer failed: " + resp);
        }
        int port = resp.path("port").asInt(0);
        if (port <= 0) {
            throw new IllegalStateException("ZLM openRtpServer returned invalid port: " + resp);
        }
        log.info("ZLM openRtpServer streamId={} port={}", streamId, port);
        return port;
    }

    public void closeRtpServer(String streamId) {
        Map<String, String> params = new HashMap<>();
        params.put("secret", props.getSecret());
        params.put("stream_id", streamId);
        try {
            JsonNode resp = get("/index/api/closeRtpServer", params);
            log.info("ZLM closeRtpServer streamId={} resp={}", streamId, resp);
        } catch (Exception e) {
            log.warn("ZLM closeRtpServer failed streamId={}: {}", streamId, e.getMessage());
        }
    }

    public boolean isMediaOnline(String streamId) {
        Map<String, String> params = new HashMap<>();
        params.put("secret", props.getSecret());
        params.put("schema", "rtsp");
        params.put("vhost", props.getVhost());
        params.put("app", props.getApp());
        params.put("stream", streamId);
        try {
            JsonNode resp = get("/index/api/getMediaInfo", params);
            return resp != null && resp.path("code").asInt(-1) == 0 && resp.path("online").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    public String buildFlvUrl(String streamId) {
        String host = trimSlash(props.getPlayHost());
        return host + "/" + props.getApp() + "/" + streamId + ".live.flv";
    }

    public String buildHlsUrl(String streamId) {
        String host = trimSlash(props.getPlayHost());
        return host + "/" + props.getApp() + "/" + streamId + "/hls.m3u8";
    }

    public String getApp() {
        return props.getApp();
    }

    public String getVhost() {
        return props.getVhost();
    }

    private JsonNode get(String path, Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(trimSlash(props.getBaseUrl()) + path);
        params.forEach(builder::queryParam);
        String url = builder.toUriString();
        try {
            String body = restTemplate.getForObject(url, String.class);
            return objectMapper.readTree(body);
        } catch (Exception e) {
            log.error("ZLM API call failed: {}", url, e);
            throw new IllegalStateException("ZLM API call failed: " + e.getMessage(), e);
        }
    }

    private static String trimSlash(String s) {
        if (s == null) {
            return "";
        }
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}

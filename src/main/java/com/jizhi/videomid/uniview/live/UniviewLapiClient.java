package com.jizhi.videomid.uniview.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jizhi.videomid.config.UniviewProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 宇视 LAPI HTTP 客户端 */
@Component
public class UniviewLapiClient {

    private static final Logger log = LoggerFactory.getLogger(UniviewLapiClient.class);

    private final UniviewProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public UniviewLapiClient(UniviewProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(8000);
        this.restTemplate = new RestTemplate(factory);
    }

    public Map<String, Object> readiness() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("apiType", props.getLive().getApiType());
        m.put("deviceHost", props.getLive().getDeviceHost());
        m.put("devicePort", props.getLive().getDevicePort());
        m.put("configured", isConfigured());
        m.put("baseUrl", baseUrl());
        return m;
    }

    public boolean isConfigured() {
        String host = props.getLive().getDeviceHost();
        return host != null && !host.isBlank();
    }

    public String baseUrl() {
        if (!isConfigured()) {
            return "";
        }
        String host = props.getLive().getDeviceHost().trim();
        int port = props.getLive().getDevicePort();
        String scheme = port == 443 ? "https" : "http";
        if (port == 80 || port == 443) {
            return scheme + "://" + host;
        }
        return scheme + "://" + host + ":" + port;
    }

    public JsonNode get(String path) {
        ensureConfigured();
        String url = baseUrl() + path;
        log.info("[Uniview-LAPI] GET {}", url);
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders());
        String body = restTemplate.exchange(url, HttpMethod.GET, entity, String.class).getBody();
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("LAPI 响应非 JSON: " + e.getMessage(), e);
        }
    }

    public JsonNode put(String path, Map<String, Object> payload) {
        ensureConfigured();
        String url = baseUrl() + path;
        log.info("[Uniview-LAPI] PUT {}", url);
        try {
            HttpHeaders headers = authHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String json = objectMapper.writeValueAsString(payload);
            HttpEntity<String> entity = new HttpEntity<>(json, headers);
            String body = restTemplate.exchange(url, HttpMethod.PUT, entity, String.class).getBody();
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("LAPI PUT 失败: " + e.getMessage(), e);
        }
    }

    /** 解析 LAPI 通道列表为统一设备结构 */
    public List<Map<String, Object>> parseDevicesFromChannels(JsonNode root) {
        List<Map<String, Object>> devices = new ArrayList<>();
        JsonNode arr = findArray(root, "ChannelDetailInfos", "Channels", "ChannelList");
        if (arr == null || !arr.isArray()) {
            return devices;
        }
        Map<String, Object> device = new LinkedHashMap<>();
        device.put("deviceId", props.getLive().getDeviceHost());
        device.put("name", "宇视设备-" + props.getLive().getDeviceHost());
        device.put("manufacturer", "宇视");
        device.put("model", text(root, "Model", "DeviceModel"));
        List<Map<String, Object>> channels = new ArrayList<>();
        for (JsonNode ch : arr) {
            Map<String, Object> cv = new LinkedHashMap<>();
            cv.put("channelId", text(ch, "ChannelID", "ChannelId", "ID"));
            cv.put("streamName", text(ch, "Name", "ChannelName"));
            cv.put("channelType", text(ch, "ChannelType", "Type"));
            cv.put("streamType", "main");
            channels.add(cv);
        }
        device.put("channels", channels);
        devices.add(device);
        return devices;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String user = props.getLive().getUsername();
        String pass = props.getLive().getPassword();
        if (user != null && !user.isBlank()) {
            String token = Base64.getEncoder().encodeToString(
                    (user + ":" + (pass == null ? "" : pass)).getBytes(StandardCharsets.UTF_8));
            headers.set("Authorization", "Basic " + token);
        }
        return headers;
    }

    private static JsonNode findArray(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode n = root.findValue(name);
            if (n != null && n.isArray()) {
                return n;
            }
        }
        JsonNode data = root.path("Response").path("Data");
        if (!data.isMissingNode()) {
            for (String name : names) {
                JsonNode n = data.get(name);
                if (n != null && n.isArray()) {
                    return n;
                }
            }
        }
        return null;
    }

    private static String text(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode n = node.get(f);
            if (n != null && !n.isNull()) {
                return n.asText();
            }
        }
        return "";
    }

    void ensureConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "宇视 live 未配置 device-host，请在 application.yml 设置 uniview.live.device-host");
        }
        if (!"lapi".equalsIgnoreCase(props.getLive().getApiType())) {
            throw new UnsupportedOperationException("当前仅 LAPI，NetSDK 请实现对应客户端");
        }
    }
}

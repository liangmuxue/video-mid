package com.jizhi.videomid.uniview.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jizhi.videomid.config.UniviewProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 宇视 LAPI HTTP 客户端（HTTP Digest） */
@Component
public class UniviewLapiClient {

    private static final Logger log = LoggerFactory.getLogger(UniviewLapiClient.class);
    private static final Pattern AUTH_PARAM = Pattern.compile("(\\w+)=(?:\"([^\"]*)\"|([^,\\s]+))");

    private final UniviewProperties props;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    public UniviewLapiClient(UniviewProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
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
        return get(configuredEndpoint(), path);
    }

    public JsonNode get(LapiEndpoint endpoint, String path) {
        return readJson(exchange(endpoint, "GET", path, null));
    }

    public JsonNode put(String path, Map<String, Object> payload) {
        return put(configuredEndpoint(), path, payload);
    }

    public JsonNode put(LapiEndpoint endpoint, String path, Map<String, Object> payload) {
        return write(endpoint, "PUT", path, payload);
    }

    public JsonNode post(String path, Map<String, Object> payload) {
        return post(configuredEndpoint(), path, payload);
    }

    public JsonNode post(LapiEndpoint endpoint, String path, Map<String, Object> payload) {
        return write(endpoint, "POST", path, payload);
    }

    private LapiEndpoint configuredEndpoint() {
        ensureConfigured();
        String username = props.getLive().getUsername() == null ? "" : props.getLive().getUsername();
        String password = props.getLive().getPassword() == null ? "" : props.getLive().getPassword();
        return new LapiEndpoint(props.getLive().getDeviceHost().trim(), props.getLive().getDevicePort(), username, password);
    }

    private JsonNode write(LapiEndpoint endpoint, String method, String path, Map<String, Object> payload) {
        try {
            String json = payload == null ? "" : objectMapper.writeValueAsString(payload);
            return readJson(exchange(endpoint, method, path, json));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("LAPI " + method + " 失败: " + e.getMessage(), e);
        }
    }

    /** 解析 LAPI 通道列表为统一设备结构 */
    public List<Map<String, Object>> parseDevicesFromChannels(JsonNode root) {
        List<Map<String, Object>> devices = new ArrayList<>();
        JsonNode arr = findArray(root, "ChannelDetailInfos", "DetailInfos", "Channels", "ChannelList");
        if (arr == null || !arr.isArray()) {
            return devices;
        }
        Map<String, Object> device = new LinkedHashMap<>();
        device.put("deviceId", props.getLive().getDeviceHost());
        device.put("name", "宇视设备-" + props.getLive().getDeviceHost());
        device.put("manufacturer", "宇视");
        device.put("model", text(root, "Model", "DeviceModel"));
        List<Map<String, Object>> channels = new ArrayList<>();
        String channelName = "";
        for (JsonNode ch : arr) {
            Map<String, Object> cv = new LinkedHashMap<>();
            cv.put("channelId", text(ch, "ChannelID", "ChannelId", "ID"));
            cv.put("streamName", text(ch, "Name", "ChannelName"));
            cv.put("channelType", text(ch, "ChannelType", "Type"));
            cv.put("streamType", "main");
            if (channelName.isBlank()) {
                channelName = String.valueOf(cv.get("streamName"));
            }
            channels.add(cv);
        }
        if (!channelName.isBlank()) {
            device.put("name", channelName);
        }
        device.put("channels", channels);
        devices.add(device);
        return devices;
    }

    private String exchange(LapiEndpoint endpoint, String method, String path, String json) {
        String url = endpoint.baseUrl() + path;
        log.info("[Uniview-LAPI] {} {}", method, url);
        HttpResult first = send(method, url, json, null);
        if (first.status == 401) {
            String challenge = first.header("WWW-Authenticate");
            if (challenge == null || !challenge.toLowerCase(Locale.ROOT).contains("digest")) {
                throw new IllegalStateException("LAPI 需要 Digest 鉴权，但未返回 WWW-Authenticate: " + path);
            }
            String authorization = digestAuthorization(endpoint, method, path, challenge);
            HttpResult second = send(method, url, json, authorization);
            if (second.status < 200 || second.status >= 300) {
                throw new IllegalStateException("LAPI " + method + " " + path + " HTTP " + second.status + " " + second.body);
            }
            return second.body;
        }
        if (first.status < 200 || first.status >= 300) {
            throw new IllegalStateException("LAPI " + method + " " + path + " HTTP " + first.status + " " + first.body);
        }
        return first.body;
    }

    private HttpResult send(String method, String url, String json, String authorization) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(8000);
            connection.setRequestMethod(method);
            connection.setInstanceFollowRedirects(false);
            if (authorization != null) {
                connection.setRequestProperty("Authorization", authorization);
            }
            if (json != null) {
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(bytes);
                }
            }
            int status = connection.getResponseCode();
            String body = readBody(connection, status);
            Map<String, List<String>> headers = new LinkedHashMap<>();
            String www = connection.getHeaderField("WWW-Authenticate");
            if (www != null) {
                headers.put("WWW-Authenticate", List.of(www));
            }
            return new HttpResult(status, body, headers);
        } catch (IOException e) {
            throw new IllegalStateException("LAPI 请求失败: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String digestAuthorization(LapiEndpoint endpoint, String method, String uri, String wwwAuthenticate) {
        Map<String, String> params = parseAuthHeader(wwwAuthenticate);
        String username = endpoint.username() == null ? "" : endpoint.username();
        String password = endpoint.password() == null ? "" : endpoint.password();
        String realm = params.getOrDefault("realm", "");
        String nonce = params.getOrDefault("nonce", "");
        String qop = params.get("qop");
        String opaque = params.get("opaque");
        String algorithm = params.getOrDefault("algorithm", "MD5");
        String cnonce = randomHex(16);
        String nc = "00000001";
        String ha1 = md5(username + ":" + realm + ":" + password);
        String ha2 = md5(method + ":" + uri);
        String response;
        if (qop != null && !qop.isBlank()) {
            String qopToken = qop.split(",")[0].trim();
            response = md5(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qopToken + ":" + ha2);
            StringBuilder header = new StringBuilder();
            header.append("Digest username=\"").append(username).append("\"");
            header.append(", realm=\"").append(realm).append("\"");
            header.append(", nonce=\"").append(nonce).append("\"");
            header.append(", uri=\"").append(uri).append("\"");
            header.append(", algorithm=").append(algorithm);
            header.append(", qop=").append(qopToken);
            header.append(", nc=").append(nc);
            header.append(", cnonce=\"").append(cnonce).append("\"");
            header.append(", response=\"").append(response).append("\"");
            if (opaque != null) {
                header.append(", opaque=\"").append(opaque).append("\"");
            }
            return header.toString();
        }
        response = md5(ha1 + ":" + nonce + ":" + ha2);
        return "Digest username=\"" + username + "\", realm=\"" + realm + "\", nonce=\"" + nonce
                + "\", uri=\"" + uri + "\", algorithm=" + algorithm + ", response=\"" + response + "\"";
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("LAPI 响应非 JSON: " + e.getMessage(), e);
        }
    }

    private static String readBody(HttpURLConnection connection, int status) throws IOException {
        InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (in == null) {
            return "";
        }
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> parseAuthHeader(String header) {
        Map<String, String> params = new LinkedHashMap<>();
        Matcher matcher = AUTH_PARAM.matcher(header);
        while (matcher.find()) {
            String value = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            params.put(matcher.group(1), value);
        }
        return params;
    }

    private static String md5(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 不可用", e);
        }
    }

    private String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        random.nextBytes(buf);
        StringBuilder hex = new StringBuilder(bytes * 2);
        for (byte b : buf) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
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

    private record HttpResult(int status, String body, Map<String, List<String>> headers) {
        String header(String name) {
            if (headers == null) {
                return null;
            }
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)
                        && entry.getValue() != null && !entry.getValue().isEmpty()) {
                    return entry.getValue().get(0);
                }
            }
            return null;
        }
    }
}

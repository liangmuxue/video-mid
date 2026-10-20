package com.jizhi.videomid.integration.jizhiai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jizhi.videomid.config.JizhiAiProperties;
import com.jizhi.videomid.device.Device;
import com.jizhi.videomid.device.DeviceFeature;
import com.jizhi.videomid.device.DeviceStatus;
import com.jizhi.videomid.device.DeviceType;
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
import java.util.List;
import java.util.Map;

/** 新增和修改都同步 {@code /aidemo/device/update}。 */
@Component
public class JizhiAiDeviceSyncClient {

    private static final Logger log = LoggerFactory.getLogger(JizhiAiDeviceSyncClient.class);
    private static final String SUCCESS_CODE = "00000000";
    private static final String UPDATE_DEVICE_PATH = "/aidemo/device/update";

    private final JizhiAiProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public JizhiAiDeviceSyncClient(JizhiAiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 设备已有记录后的同步。名称、地址、目录、状态、直播地址等任一变化都调用。
     * 缺 RTSP 或 deviceNo 时省略对应字段，仍提交。
     */
    public boolean syncDeviceUpdate(Device device, String fullStreamPath) {
        return postUpdate(device, fullStreamPath);
    }

    /** 新增设备同步，与修改走同一个 update 接口。 */
    public boolean syncDeviceSave(Device device, String fullStreamPath) {
        return postUpdate(device, fullStreamPath);
    }

    public boolean isReady() {
        JizhiAiProperties.Sync sync = properties.getSync();
        return sync.isEnabled() && sync.getBaseUrl() != null && !sync.getBaseUrl().isBlank();
    }

    private boolean postUpdate(Device device, String fullStreamPath) {
        if (device == null || device.getDeviceId() == null || device.getDeviceId().isBlank()) {
            return false;
        }
        Map<String, Object> body;
        try {
            body = buildBody(device, fullStreamPath);
        } catch (Exception e) {
            log.warn("[极知AI] 组装 update 请求失败 deviceId={} err={}", device.getDeviceId(), e.getMessage());
            return false;
        }
        return postJson(UPDATE_DEVICE_PATH, body, "update", device.getDeviceId());
    }

    private Map<String, Object> buildBody(Device device, String fullStreamPath) throws Exception {
        int deviceState = DeviceStatus.normalize(device.getStatus());
        int deviceFeature = DeviceFeature.normalize(device.getDeviceFeature());
        String deviceName = device.getName();
        if (deviceName == null || deviceName.isBlank()) {
            deviceName = device.getDeviceId();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceName", deviceName);
        body.put("deviceId", device.getDeviceId().trim());
        if (device.getDeviceNo() != null && !device.getDeviceNo().isBlank()) {
            body.put("buildingNo", device.getDeviceNo().trim());
        }
        if (fullStreamPath != null && !fullStreamPath.isBlank()) {
            body.put("fullStreamPath", fullStreamPath.trim());
        }
        body.put("key", objectMapper.writeValueAsString(syncKeys(deviceFeature)));
        body.put("isPush", String.valueOf(deviceFeature));
        body.put("deviceState", deviceState);
        if (device.getAddress() != null && !device.getAddress().isBlank()) {
            body.put("address", device.getAddress().trim());
        }
        if (device.getFolderId() != null) {
            body.put("folderId", String.valueOf(device.getFolderId()));
        }
        body.put("playable", !DeviceStatus.isDisabled(deviceState));
        body.put("livePlayable", DeviceStatus.isEnabled(deviceState));
        putText(body, "cameraIp", device.getCameraIp());
        if (device.getCameraPort() != null) {
            body.put("cameraPort", String.valueOf(device.getCameraPort()));
        }
        putText(body, "loginName", device.getCameraUsername());
        putText(body, "password", device.getCameraPassword());
        if (device.getDeviceType() != null) {
            body.put("deviceType", DeviceType.normalize(device.getDeviceType()));
        }
        return body;
    }

    /** 同步给极知的算法列表。0 抓拍不变；1 视频流含 photo、snap_face；2 含 snap_face、person。 */
    private static List<String> syncKeys(int deviceFeature) {
        return switch (deviceFeature) {
            case DeviceFeature.SNAPSHOT -> List.of("snap_face");
            case DeviceFeature.BOTH -> List.of("snap_face", "person");
            default -> List.of("photo", "snap_face");
        };
    }

    private static void putText(Map<String, Object> body, String key, String value) {
        if (value != null && !value.isBlank()) {
            body.put(key, value.trim());
        }
    }

    private boolean postJson(String path, Map<String, Object> body, String op, String deviceId) {
        JizhiAiProperties.Sync sync = properties.getSync();
        if (!sync.isEnabled()) {
            return false;
        }
        String base = sync.getBaseUrl();
        if (base == null || base.isBlank()) {
            log.warn("[极知AI] 未配置 jizhi-ai.sync.base-url，跳过 {} deviceId={}", op, deviceId);
            return false;
        }
        String url = trimSlash(base) + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            String payload = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("[极知AI] {} HTTP {} deviceId={} body={}",
                        op, response.getStatusCode().value(), deviceId, payload);
                return false;
            }
            if (payload == null || payload.isBlank()) {
                log.info("[极知AI] {} 已调用 deviceId={}", op, deviceId);
                return true;
            }
            JsonNode root = objectMapper.readTree(payload);
            String code = root.path("code").asText("");
            if (SUCCESS_CODE.equals(code)) {
                log.info("[极知AI] {} 成功 deviceId={}", op, deviceId);
                return true;
            }
            log.warn("[极知AI] {} 业务失败 deviceId={} code={} message={}",
                    op, deviceId, code, root.path("message").asText(""));
            return false;
        } catch (Exception e) {
            log.warn("[极知AI] {} 请求失败 deviceId={} url={} err={}", op, deviceId, url, e.getMessage());
            return false;
        }
    }

    private static String trimSlash(String base) {
        String b = base.trim();
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        return b;
    }
}

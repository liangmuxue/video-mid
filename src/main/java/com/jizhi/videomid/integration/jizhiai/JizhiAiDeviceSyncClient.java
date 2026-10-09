package com.jizhi.videomid.integration.jizhiai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jizhi.videomid.config.JizhiAiProperties;
import com.jizhi.videomid.device.Device;
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
import java.util.Map;

/** 新增/状态变更时同步极知 AI {@code /aidemo/device/save}、{@code /aidemo/device/updateStatus}。 */
@Component
public class JizhiAiDeviceSyncClient {

    private static final Logger log = LoggerFactory.getLogger(JizhiAiDeviceSyncClient.class);
    private static final String SUCCESS_CODE = "00000000";
    private static final String UPDATE_STATUS_PATH = "/aidemo/device/updateStatus";
    private static final String SAVE_DEVICE_PATH = "/aidemo/device/save";

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
     * @param deviceId    业务设备编号（与中台 device_id 一致）
     * @param deviceState 0=不可用 1=已启用 2=已停用
     */
    public void syncDeviceStatus(String deviceId, int deviceState) {
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceId", deviceId.trim());
        body.put("deviceState", deviceState);
        postJson(UPDATE_STATUS_PATH, body, "updateStatus", deviceId);
    }

    /**
     * 新增设备同步；无 RTSP、无 deviceNo（buildingNo）时跳过。
     *
     * @param fullStreamPath 业务直播 RTSP，为空则不同步
     */
    public void syncDeviceSave(Device device, String fullStreamPath) {
        if (device == null || device.getDeviceId() == null || device.getDeviceId().isBlank()) {
            return;
        }
        if (fullStreamPath == null || fullStreamPath.isBlank()) {
            log.info("[极知AI] 无可用 RTSP，跳过 save 同步 deviceId={}", device.getDeviceId());
            return;
        }
        if (device.getDeviceNo() == null) {
            log.info("[极知AI] 未填 deviceNo（buildingNo），跳过 save 同步 deviceId={}", device.getDeviceId());
            return;
        }
        Map<String, Object> body;
        try {
            body = buildSaveBody(device, fullStreamPath.trim());
        } catch (Exception e) {
            log.warn("[极知AI] 组装 save 请求失败 deviceId={} err={}", device.getDeviceId(), e.getMessage());
            return;
        }
        postJson(SAVE_DEVICE_PATH, body, "save", device.getDeviceId());
    }

    private Map<String, Object> buildSaveBody(Device device, String fullStreamPath) throws Exception {
        int deviceState = DeviceStatus.normalize(device.getStatus());
        int deviceType = DeviceType.normalize(device.getDeviceType());
        String deviceName = device.getName();
        if (deviceName == null || deviceName.isBlank()) {
            deviceName = device.getDeviceId();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceName", deviceName);
        body.put("deviceId", device.getDeviceId().trim());
        body.put("buildingNo", String.valueOf(device.getDeviceNo()));
        body.put("fullStreamPath", fullStreamPath);
        body.put("key", objectMapper.writeValueAsString(DeviceType.capabilityKeys(deviceType)));
        body.put("isPush", String.valueOf(deviceType));
        body.put("deviceState", deviceState);
        if (device.getAddress() != null && !device.getAddress().isBlank()) {
            body.put("address", device.getAddress().trim());
        }
        if (device.getFolderId() != null) {
            body.put("folderId", String.valueOf(device.getFolderId()));
        }
        body.put("playable", !DeviceStatus.isDisabled(deviceState));
        body.put("livePlayable", DeviceStatus.isEnabled(deviceState));
        return body;
    }

    private void postJson(String path, Map<String, Object> body, String op, String deviceId) {
        JizhiAiProperties.Sync sync = properties.getSync();
        if (!sync.isEnabled()) {
            return;
        }
        String base = sync.getBaseUrl();
        if (base == null || base.isBlank()) {
            log.warn("[极知AI] 未配置 jizhi-ai.sync.base-url，跳过 {} deviceId={}", op, deviceId);
            return;
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
                return;
            }
            if (payload == null || payload.isBlank()) {
                log.info("[极知AI] {} 已调用 deviceId={}", op, deviceId);
                return;
            }
            JsonNode root = objectMapper.readTree(payload);
            String code = root.path("code").asText("");
            if (SUCCESS_CODE.equals(code)) {
                log.info("[极知AI] {} 成功 deviceId={}", op, deviceId);
            } else {
                log.warn("[极知AI] {} 业务失败 deviceId={} code={} message={}",
                        op, deviceId, code, root.path("message").asText(""));
            }
        } catch (Exception e) {
            log.warn("[极知AI] {} 请求失败 deviceId={} url={} err={}", op, deviceId, url, e.getMessage());
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

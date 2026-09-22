package com.jizhi.videomid.api;

import com.jizhi.videomid.auth.dto.ApiResponse;
import com.jizhi.videomid.device.DeviceFolderService;
import com.jizhi.videomid.device.DeviceService;
import com.jizhi.videomid.device.DeviceStatus;
import com.jizhi.videomid.device.DeviceStream;
import com.jizhi.videomid.record.RecordClipItemRequest;
import com.jizhi.videomid.record.RecordClipService;
import com.jizhi.videomid.record.RecordFileService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务端只读能力：目录树、设备、直播、录像。
 */
@RestController
@RequestMapping("/api/biz")
public class BizPortalController {

    private final DeviceFolderService folderService;
    private final DeviceService deviceService;
    private final RecordFileService recordFileService;
    private final RecordClipService recordClipService;

    @Value("${open-api.public-base-url:}")
    private String publicBaseUrl;

    public BizPortalController(DeviceFolderService folderService,
                               DeviceService deviceService,
                               RecordFileService recordFileService,
                               RecordClipService recordClipService) {
        this.folderService = folderService;
        this.deviceService = deviceService;
        this.recordFileService = recordFileService;
        this.recordClipService = recordClipService;
    }

    @GetMapping("/folders/tree")
    public ApiResponse<List<Map<String, Object>>> tree() {
        return ApiResponse.ok(folderService.tree());
    }

    @GetMapping("/devices")
    public ApiResponse<List<Map<String, Object>>> devices(
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false, defaultValue = "true") boolean includeChildren) {
        List<Map<String, Object>> list = deviceService.listDevices(folderId, includeChildren);
        for (Map<String, Object> m : list) {
            int status = DeviceStatus.normalize(m.get("status"));
            m.put("playable", !DeviceStatus.isDisabled(status));
            m.put("livePlayable", DeviceStatus.isEnabled(status));
        }
        return ApiResponse.ok(list);
    }

    @GetMapping("/devices/{deviceId}")
    public ApiResponse<Map<String, Object>> device(@PathVariable String deviceId) {
        Map<String, Object> detail = deviceService.getDeviceByDeviceId(deviceId);
        int status = DeviceStatus.normalize(detail.get("status"));
        detail.put("playable", !DeviceStatus.isDisabled(status));
        detail.put("livePlayable", DeviceStatus.isEnabled(status));
        DeviceStream live = deviceService.resolveLiveStream(deviceId).orElse(null);
        if (live != null) {
            Map<String, Object> liveView = new HashMap<>();
            liveView.put("id", live.getId());
            liveView.put("streamType", live.getStreamType());
            liveView.put("streamUrl", live.getStreamUrl());
            liveView.put("streamName", live.getStreamName());
            liveView.put("liveEnabled", true);
            detail.put("liveStream", liveView);
        } else {
            detail.put("liveStream", null);
        }
        return ApiResponse.ok(detail);
    }

    @PostMapping("/devices/{deviceId}/live")
    public ApiResponse<Map<String, Object>> startLive(@PathVariable String deviceId) {
        Map<String, Object> detail = deviceService.getDeviceByDeviceId(deviceId);
        int status = DeviceStatus.normalize(detail.get("status"));
        if (!DeviceStatus.isEnabled(status)) {
            throw new IllegalArgumentException("仅「已启用」设备可直播");
        }
        return ApiResponse.ok(deviceService.startBizLive(deviceId));
    }

    /** 某月内有录像的日期 yyyy-MM-dd */
    @GetMapping("/devices/{deviceId}/recording-days")
    public ApiResponse<List<String>> recordingDays(
            @PathVariable String deviceId,
            @RequestParam int year,
            @RequestParam int month) {
        Map<String, Object> detail = deviceService.getDeviceByDeviceId(deviceId);
        int status = DeviceStatus.normalize(detail.get("status"));
        if (DeviceStatus.isDisabled(status)) {
            throw new IllegalArgumentException("设备已停用，无法回放");
        }
        return ApiResponse.ok(recordFileService.listRecordingDays(deviceId, year, month));
    }

    /** from / to 为毫秒时间戳（可选） */
    @GetMapping("/devices/{deviceId}/recordings")
    public ApiResponse<List<Map<String, Object>>> recordings(
            @PathVariable String deviceId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            HttpServletRequest request) {
        Map<String, Object> detail = deviceService.getDeviceByDeviceId(deviceId);
        int status = DeviceStatus.normalize(detail.get("status"));
        if (DeviceStatus.isDisabled(status)) {
            throw new IllegalArgumentException("设备已停用，无法回放");
        }
        String base = publicBaseUrl == null || publicBaseUrl.isBlank()
                ? request.getScheme() + "://" + request.getServerName()
                + ((request.getServerPort() == 80 || request.getServerPort() == 443) ? "" : ":" + request.getServerPort())
                : publicBaseUrl.replaceAll("/$", "");
        return ApiResponse.ok(recordFileService.listWithVideoUrls(deviceId, from, to, base));
    }

    /** 批量截取录像片段（JSON 数组） */
    @PostMapping("/clips")
    public ApiResponse<List<Map<String, Object>>> clipsBatch(@RequestBody List<RecordClipItemRequest> items,
                                                            HttpServletRequest request) {
        String base = publicBaseUrl == null || publicBaseUrl.isBlank()
                ? request.getScheme() + "://" + request.getServerName()
                + ((request.getServerPort() == 80 || request.getServerPort() == 443) ? "" : ":" + request.getServerPort())
                : publicBaseUrl.replaceAll("/$", "");
        String finalBase = base;
        return ApiResponse.ok(recordClipService.clipInfoBatch(items,
                (deviceId, at, seconds) -> finalBase + "/api/biz/devices/" + encodePath(deviceId) + "/clip/file"
                        + "?at=" + encodeQuery(at)
                        + (seconds != null ? "&seconds=" + seconds : ""),
                this::assertDeviceEnabled));
    }

    /** 按时间点截取录像片段：时间戳前后各 seconds 秒 */
    @GetMapping("/devices/{deviceId}/clip")
    public ApiResponse<Map<String, Object>> clip(@PathVariable String deviceId,
                                                 @RequestParam String at,
                                                 @RequestParam(required = false) Integer seconds,
                                                 HttpServletRequest request) {
        assertDeviceEnabled(deviceId);
        String base = publicBaseUrl == null || publicBaseUrl.isBlank()
                ? request.getScheme() + "://" + request.getServerName()
                + ((request.getServerPort() == 80 || request.getServerPort() == 443) ? "" : ":" + request.getServerPort())
                : publicBaseUrl.replaceAll("/$", "");
        String videoUrl = base + "/api/biz/devices/" + encodePath(deviceId) + "/clip/file"
                + "?at=" + encodeQuery(at)
                + (seconds != null ? "&seconds=" + seconds : "");
        return ApiResponse.ok(recordClipService.clipInfo(deviceId, at, seconds, videoUrl));
    }

    /** 片段 MP4 直链 */
    @GetMapping("/devices/{deviceId}/clip/file")
    public ResponseEntity<Resource> clipFile(@PathVariable String deviceId,
                                             @RequestParam String at,
                                             @RequestParam(required = false) Integer seconds) {
        assertDeviceEnabled(deviceId);
        Resource resource = recordClipService.openClip(deviceId, at, seconds);
        String fileName = deviceId + "_" + at + ".mp4";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(resource);
    }

    private void assertDeviceEnabled(String deviceId) {
        Map<String, Object> detail = deviceService.getDeviceByDeviceId(deviceId);
        int status = DeviceStatus.normalize(detail.get("status"));
        if (DeviceStatus.isDisabled(status)) {
            throw new IllegalArgumentException("设备已停用，无法回放");
        }
    }

    private static String encodePath(String raw) {
        try {
            return java.net.URLEncoder.encode(raw, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("+", "%20");
        } catch (Exception e) {
            return raw;
        }
    }

    private static String encodeQuery(String raw) {
        try {
            return java.net.URLEncoder.encode(raw, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return raw;
        }
    }
}

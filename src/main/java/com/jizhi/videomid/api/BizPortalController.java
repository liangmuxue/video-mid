package com.jizhi.videomid.api;

import com.jizhi.videomid.auth.dto.ApiResponse;
import com.jizhi.videomid.biz.dto.BizDeviceIdRequest;
import com.jizhi.videomid.biz.dto.BizDeviceListRequest;
import com.jizhi.videomid.biz.dto.BizRecordingDaysRequest;
import com.jizhi.videomid.biz.dto.BizRecordingsRequest;
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

    @PostMapping("/devices/list")
    public ApiResponse<List<Map<String, Object>>> devices(@RequestBody(required = false) BizDeviceListRequest req) {
        Long folderId = req == null ? null : req.getFolderId();
        boolean includeChildren = req == null || req.getIncludeChildren() == null || req.getIncludeChildren();
        return ApiResponse.ok(listDevices(folderId, includeChildren));
    }

    @PostMapping("/devices/detail")
    public ApiResponse<Map<String, Object>> device(@RequestBody BizDeviceIdRequest req) {
        return ApiResponse.ok(deviceDetail(requireDeviceId(req == null ? null : req.getDeviceId())));
    }

    @PostMapping("/devices/live")
    public ApiResponse<Map<String, Object>> startLive(@RequestBody BizDeviceIdRequest req) {
        return ApiResponse.ok(startLiveById(requireDeviceId(req == null ? null : req.getDeviceId())));
    }

    @PostMapping("/devices/recording-days")
    public ApiResponse<List<String>> recordingDays(@RequestBody BizRecordingDaysRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (req.getYear() == null) {
            throw new IllegalArgumentException("year 不能为空");
        }
        if (req.getMonth() == null) {
            throw new IllegalArgumentException("month 不能为空");
        }
        return ApiResponse.ok(recordingDaysById(requireDeviceId(req.getDeviceId()), req.getYear(), req.getMonth()));
    }

    @PostMapping("/devices/recordings")
    public ApiResponse<List<Map<String, Object>>> recordings(@RequestBody BizRecordingsRequest req,
                                                             HttpServletRequest request) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        return ApiResponse.ok(recordingsById(requireDeviceId(req.getDeviceId()),
                req.fromAsString(), req.toAsString(), request));
    }

    /** 批量截取录像片段（JSON 数组） */
    @PostMapping("/clips")
    public ApiResponse<List<Map<String, Object>>> clipsBatch(@RequestBody List<RecordClipItemRequest> items,
                                                            HttpServletRequest request) {
        String base = publicBaseUrl(request);
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
        String base = publicBaseUrl(request);
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

    private List<Map<String, Object>> listDevices(Long folderId, boolean includeChildren) {
        List<Map<String, Object>> list = deviceService.listDevices(folderId, includeChildren);
        for (Map<String, Object> m : list) {
            int status = DeviceStatus.normalize(m.get("status"));
            m.put("playable", !DeviceStatus.isDisabled(status));
            m.put("livePlayable", DeviceStatus.isEnabled(status));
        }
        return list;
    }

    private Map<String, Object> deviceDetail(String deviceId) {
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
        return detail;
    }

    private Map<String, Object> startLiveById(String deviceId) {
        Map<String, Object> detail = deviceService.getDeviceByDeviceId(deviceId);
        int status = DeviceStatus.normalize(detail.get("status"));
        if (!DeviceStatus.isEnabled(status)) {
            throw new IllegalArgumentException("仅「已启用」设备可直播");
        }
        return deviceService.startBizLive(deviceId);
    }

    private List<String> recordingDaysById(String deviceId, int year, int month) {
        Map<String, Object> detail = deviceService.getDeviceByDeviceId(deviceId);
        int status = DeviceStatus.normalize(detail.get("status"));
        if (DeviceStatus.isDisabled(status)) {
            throw new IllegalArgumentException("设备已停用，无法回放");
        }
        return recordFileService.listRecordingDays(deviceId, year, month);
    }

    private List<Map<String, Object>> recordingsById(String deviceId, String from, String to,
                                                     HttpServletRequest request) {
        Map<String, Object> detail = deviceService.getDeviceByDeviceId(deviceId);
        int status = DeviceStatus.normalize(detail.get("status"));
        if (DeviceStatus.isDisabled(status)) {
            throw new IllegalArgumentException("设备已停用，无法回放");
        }
        return recordFileService.listWithVideoUrls(deviceId, from, to, publicBaseUrl(request));
    }

    private String publicBaseUrl(HttpServletRequest request) {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return request.getScheme() + "://" + request.getServerName()
                    + ((request.getServerPort() == 80 || request.getServerPort() == 443) ? "" : ":" + request.getServerPort());
        }
        return publicBaseUrl.replaceAll("/$", "");
    }

    private void assertDeviceEnabled(String deviceId) {
        Map<String, Object> detail = deviceService.getDeviceByDeviceId(deviceId);
        int status = DeviceStatus.normalize(detail.get("status"));
        if (DeviceStatus.isDisabled(status)) {
            throw new IllegalArgumentException("设备已停用，无法回放");
        }
    }

    private static String requireDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId 不能为空");
        }
        return deviceId.trim();
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

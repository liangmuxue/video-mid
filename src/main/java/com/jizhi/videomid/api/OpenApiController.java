package com.jizhi.videomid.api;

import com.jizhi.videomid.auth.dto.ApiResponse;
import com.jizhi.videomid.device.DeviceService;
import com.jizhi.videomid.record.RecordClipService;
import com.jizhi.videomid.record.RecordFileService;
import com.jizhi.videomid.uniview.nvr.RecordingCatalog;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.jizhi.videomid.record.RecordClipItemRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 对外开放接口（无需登录鉴权）。
 */
@RestController
@RequestMapping("/api/open")
public class OpenApiController {

    private final DeviceService deviceService;
    private final RecordingCatalog recordingCatalog;
    private final RecordFileService recordFileService;
    private final RecordClipService recordClipService;

    /** 对外返回的绝对地址前缀；为空则按当前请求自动拼接 */
    @Value("${open-api.public-base-url:}")
    private String publicBaseUrl;

    public OpenApiController(DeviceService deviceService, RecordingCatalog recordingCatalog,
                             RecordFileService recordFileService, RecordClipService recordClipService) {
        this.deviceService = deviceService;
        this.recordingCatalog = recordingCatalog;
        this.recordFileService = recordFileService;
        this.recordClipService = recordClipService;
    }

    /**
     * 1. 查询设备：可按 name、deviceId 筛选（均可选，支持模糊匹配）
     */
    @GetMapping("/devices")
    public ApiResponse<List<Map<String, Object>>> searchDevices(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String deviceId) {
        return ApiResponse.ok(deviceService.searchDevices(name, deviceId));
    }

    /**
     * 2. 查询设备下全部码流
     */
    @GetMapping("/devices/{deviceId}/streams")
    public ApiResponse<List<Map<String, Object>>> listStreams(@PathVariable String deviceId) {
        return ApiResponse.ok(deviceService.listStreamsByDeviceId(deviceId));
    }

    /** 某月内有录像的日期 yyyy-MM-dd */
    @GetMapping("/devices/{deviceId}/recording-days")
    public ApiResponse<List<String>> recordingDays(
            @PathVariable String deviceId,
            @RequestParam int year,
            @RequestParam int month) {
        return ApiResponse.ok(recordingCatalog.listRecordingDays(deviceId, year, month));
    }

    /**
     * 3. 按 deviceId + 时间戳查询历史录像，返回视频访问链接
     * from / to 可选，毫秒时间戳
     */
    @GetMapping("/devices/{deviceId}/recordings")
    public ApiResponse<List<Map<String, Object>>> listRecordings(
            @PathVariable String deviceId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            HttpServletRequest request) {
        return ApiResponse.ok(recordingCatalog.listWithVideoUrls(
                deviceId, from, to, resolvePublicBase(request)));
    }

    /**
     * 4. 按时间点截取录像片段：时间戳前后各 seconds 秒。
     * 参数：deviceId、at（毫秒时间戳）、seconds（秒，默认 30）。
     * 返回 videoUrl、startTime、endTime（毫秒）。
     */
    @GetMapping("/devices/{deviceId}/clip")
    public ApiResponse<Map<String, Object>> clip(@PathVariable String deviceId,
                                                 @RequestParam String at,
                                                 @RequestParam(required = false) Integer seconds,
                                                 HttpServletRequest request) {
        String videoUrl = buildOpenClipFileUrl(resolvePublicBase(request), deviceId, at, seconds);
        return ApiResponse.ok(recordClipService.clipInfo(deviceId, at, seconds, videoUrl));
    }

    /** 片段 MP4 直链（供 videoUrl 播放/下载） */
    @GetMapping("/devices/{deviceId}/clip/file")
    public ResponseEntity<Resource> clipFile(@PathVariable String deviceId,
                                             @RequestParam String at,
                                             @RequestParam(required = false) Integer seconds) {
        Resource resource = recordClipService.openClip(deviceId, at, seconds);
        String fileName = deviceId + "_" + at + ".mp4";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(resource);
    }

    /**
     * 5. 批量截取录像片段。
     * Body 为 JSON 数组，每项：deviceId、at（毫秒时间戳）、seconds（前后各 N 秒）。
     */
    @PostMapping("/clips")
    public ApiResponse<List<Map<String, Object>>> clipsBatch(@RequestBody List<RecordClipItemRequest> items,
                                                             HttpServletRequest request) {
        String base = resolvePublicBase(request);
        return ApiResponse.ok(recordClipService.clipInfoBatch(items,
                (deviceId, at, seconds) -> buildOpenClipFileUrl(base, deviceId, at, seconds),
                null));
    }

    /** 与 /clip 相同，保留兼容 */
    @GetMapping("/devices/{deviceId}/clip/info")
    public ApiResponse<Map<String, Object>> clipInfo(@PathVariable String deviceId,
                                                     @RequestParam String at,
                                                     @RequestParam(required = false) Integer seconds,
                                                     HttpServletRequest request) {
        return clip(deviceId, at, seconds, request);
    }

    private static String buildOpenClipFileUrl(String base, String deviceId, String at, Integer seconds) {
        StringBuilder url = new StringBuilder(base)
                .append("/api/open/devices/")
                .append(encodePath(deviceId))
                .append("/clip/file?at=")
                .append(encodeQuery(at));
        if (seconds != null) {
            url.append("&seconds=").append(seconds);
        }
        return url.toString();
    }

    /** 录像文件直链（无鉴权，供对外 videoUrl 访问） */
    @GetMapping("/recordings/{deviceId}/{fileName}")
    public ResponseEntity<Resource> recordingFile(@PathVariable String deviceId,
                                                  @PathVariable String fileName) {
        Resource resource = recordFileService.openFile(deviceId, fileName);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(resource);
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

    private String resolvePublicBase(HttpServletRequest request) {
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            return publicBaseUrl.trim().replaceAll("/+$", "");
        }
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443);
        if (defaultPort) {
            return scheme + "://" + host;
        }
        return scheme + "://" + host + ":" + port;
    }
}

package com.jizhi.videomid.api;

import com.jizhi.videomid.auth.dto.ApiResponse;
import com.jizhi.videomid.record.RecordClipItemRequest;
import com.jizhi.videomid.record.RecordClipService;
import com.jizhi.videomid.record.RecordFileService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recordings")
public class RecordFileController {

    private final RecordFileService recordFileService;
    private final RecordClipService recordClipService;

    @Value("${open-api.public-base-url:}")
    private String publicBaseUrl;

    public RecordFileController(RecordFileService recordFileService, RecordClipService recordClipService) {
        this.recordFileService = recordFileService;
        this.recordClipService = recordClipService;
    }

    /**
     * 查询设备录制视频。
     * from / to 为毫秒时间戳筛选，可选
     */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestParam String deviceId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ApiResponse.ok(recordFileService.list(deviceId, from, to));
    }

    /** 某月内有录像的日期 yyyy-MM-dd */
    @GetMapping("/days")
    public ApiResponse<List<String>> days(
            @RequestParam String deviceId,
            @RequestParam int year,
            @RequestParam int month) {
        return ApiResponse.ok(recordFileService.listRecordingDays(deviceId, year, month));
    }

    /**
     * 按时间点截取录像片段：时间戳前后各 seconds 秒。
     * 参数：deviceId、at（毫秒时间戳）、seconds（秒，默认 30）。
     */
    @GetMapping("/clip")
    public ApiResponse<Map<String, Object>> clip(@RequestParam String deviceId,
                                                 @RequestParam String at,
                                                 @RequestParam(required = false) Integer seconds,
                                                 HttpServletRequest request) {
        String videoUrl = buildAdminClipFileUrl(resolvePublicBase(request), deviceId, at, seconds);
        return ApiResponse.ok(recordClipService.clipInfo(deviceId, at, seconds, videoUrl));
    }

    /** 批量截取录像片段（JSON 数组） */
    @PostMapping("/clips")
    public ApiResponse<List<Map<String, Object>>> clipsBatch(@RequestBody List<RecordClipItemRequest> items,
                                                             HttpServletRequest request) {
        String base = resolvePublicBase(request);
        return ApiResponse.ok(recordClipService.clipInfoBatch(items,
                (deviceId, at, seconds) -> buildAdminClipFileUrl(base, deviceId, at, seconds),
                null));
    }

    /** 片段 MP4 直链（供 videoUrl 播放/下载） */
    @GetMapping("/clip/file")
    public ResponseEntity<Resource> clipFile(@RequestParam String deviceId,
                                             @RequestParam String at,
                                             @RequestParam(required = false) Integer seconds) {
        Resource resource = recordClipService.openClip(deviceId, at, seconds);
        String fileName = deviceId + "_" + at + ".mp4";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(resource);
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

    private static String buildAdminClipFileUrl(String base, String deviceId, String at, Integer seconds) {
        StringBuilder url = new StringBuilder(base)
                .append("/api/recordings/clip/file?deviceId=")
                .append(encodeQuery(deviceId))
                .append("&at=")
                .append(encodeQuery(at));
        if (seconds != null) {
            url.append("&seconds=").append(seconds);
        }
        return url.toString();
    }

    private static String encodeQuery(String raw) {
        try {
            return java.net.URLEncoder.encode(raw, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return raw;
        }
    }

    /** 下载 / 播放某个录像文件 */
    @GetMapping("/{deviceId}/{fileName}")
    public ResponseEntity<Resource> file(@PathVariable String deviceId,
                                         @PathVariable String fileName) {
        Resource resource = recordFileService.openFile(deviceId, fileName);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(resource);
    }
}

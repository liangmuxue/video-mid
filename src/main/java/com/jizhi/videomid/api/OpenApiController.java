package com.jizhi.videomid.api;

import com.jizhi.videomid.auth.dto.ApiResponse;
import com.jizhi.videomid.device.DeviceService;
import com.jizhi.videomid.record.RecordFileService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final RecordFileService recordFileService;

    /** 对外返回的绝对地址前缀；为空则按当前请求自动拼接 */
    @Value("${open-api.public-base-url:}")
    private String publicBaseUrl;

    public OpenApiController(DeviceService deviceService, RecordFileService recordFileService) {
        this.deviceService = deviceService;
        this.recordFileService = recordFileService;
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

    /**
     * 3. 按 deviceId + 时间戳查询历史录像，返回视频访问链接
     * from / to 可选，格式：yyyyMMdd_HHmmss 或 yyyy-MM-dd HH:mm:ss
     */
    @GetMapping("/devices/{deviceId}/recordings")
    public ApiResponse<List<Map<String, Object>>> listRecordings(
            @PathVariable String deviceId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            HttpServletRequest request) {
        return ApiResponse.ok(recordFileService.listWithVideoUrls(
                deviceId, from, to, resolvePublicBase(request)));
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

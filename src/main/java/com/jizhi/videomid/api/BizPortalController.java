package com.jizhi.videomid.api;

import com.jizhi.videomid.auth.dto.ApiResponse;
import com.jizhi.videomid.device.DeviceFolderService;
import com.jizhi.videomid.device.DeviceService;
import com.jizhi.videomid.device.DeviceStatus;
import com.jizhi.videomid.device.DeviceStream;
import com.jizhi.videomid.record.RecordFileService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${open-api.public-base-url:}")
    private String publicBaseUrl;

    public BizPortalController(DeviceFolderService folderService,
                               DeviceService deviceService,
                               RecordFileService recordFileService) {
        this.folderService = folderService;
        this.deviceService = deviceService;
        this.recordFileService = recordFileService;
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
        // 业务端全部可见（含已停用灰色）；附带可否播放标记
        for (Map<String, Object> m : list) {
            String status = String.valueOf(m.getOrDefault("status", ""));
            boolean disabled = DeviceStatus.DISABLED.equals(DeviceStatus.normalize(status));
            // 已停用：可见不可播；已启用/不可用：可尝试回放，直播仅已启用
            m.put("playable", !disabled);
            m.put("livePlayable", DeviceStatus.ENABLED.equals(DeviceStatus.normalize(status)));
        }
        return ApiResponse.ok(list);
    }

    @GetMapping("/devices/{deviceId}")
    public ApiResponse<Map<String, Object>> device(@PathVariable String deviceId) {
        Map<String, Object> detail = deviceService.getDeviceByDeviceId(deviceId);
        String status = String.valueOf(detail.getOrDefault("status", ""));
        boolean disabled = DeviceStatus.DISABLED.equals(DeviceStatus.normalize(status));
        detail.put("playable", !disabled);
        detail.put("livePlayable", DeviceStatus.ENABLED.equals(DeviceStatus.normalize(status)));
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
        String status = String.valueOf(detail.getOrDefault("status", ""));
        if (!DeviceStatus.ENABLED.equals(DeviceStatus.normalize(status))) {
            throw new IllegalArgumentException("仅「已启用」设备可直播");
        }
        return ApiResponse.ok(deviceService.startBizLive(deviceId));
    }

    @GetMapping("/devices/{deviceId}/recordings")
    public ApiResponse<List<Map<String, Object>>> recordings(
            @PathVariable String deviceId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            HttpServletRequest request) {
        // 已停用：灰色可见但不可播（含回放）
        Map<String, Object> detail = deviceService.getDeviceByDeviceId(deviceId);
        String status = String.valueOf(detail.getOrDefault("status", ""));
        if (DeviceStatus.DISABLED.equals(DeviceStatus.normalize(status))) {
            throw new IllegalArgumentException("设备已停用，无法回放");
        }
        String base = publicBaseUrl == null || publicBaseUrl.isBlank()
                ? request.getScheme() + "://" + request.getServerName()
                + ((request.getServerPort() == 80 || request.getServerPort() == 443) ? "" : ":" + request.getServerPort())
                : publicBaseUrl.replaceAll("/$", "");
        return ApiResponse.ok(recordFileService.listWithVideoUrls(deviceId, from, to, base));
    }
}

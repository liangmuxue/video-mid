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
}

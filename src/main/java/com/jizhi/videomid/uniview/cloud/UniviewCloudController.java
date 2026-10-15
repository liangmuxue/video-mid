package com.jizhi.videomid.uniview.cloud;

import com.jizhi.videomid.auth.dto.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/uniview-cloud")
public class UniviewCloudController {

    private final UniviewCloudSyncService syncService;
    private final UniviewCloudPlayService playService;

    public UniviewCloudController(UniviewCloudSyncService syncService, UniviewCloudPlayService playService) {
        this.syncService = syncService;
        this.playService = playService;
    }

    @PostMapping("/sync")
    public ApiResponse<Map<String, Object>> sync() {
        return ApiResponse.ok(syncService.sync());
    }

    @PostMapping("/live/stop")
    public ApiResponse<Map<String, Object>> stop(@RequestBody Map<String, Object> body) {
        Object deviceId = body == null ? null : body.get("deviceId");
        if (deviceId == null || String.valueOf(deviceId).isBlank()) {
            throw new IllegalArgumentException("deviceId 不能为空");
        }
        playService.stopLive(String.valueOf(deviceId).trim());
        return ApiResponse.ok(Map.of("deviceId", String.valueOf(deviceId).trim(), "stopped", true));
    }
}

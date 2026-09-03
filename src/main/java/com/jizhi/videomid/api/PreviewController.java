package com.jizhi.videomid.api;

import com.jizhi.videomid.api.dto.ApiResponse;
import com.jizhi.videomid.api.dto.PreviewStartRequest;
import com.jizhi.videomid.api.dto.PreviewStopRequest;
import com.jizhi.videomid.session.StreamSessionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * POST /preview/start | /preview/stop — 直播预览，业务与 SFGO 共用。
 */
@RestController
public class PreviewController {

    private final StreamSessionService streamSessionService;

    public PreviewController(StreamSessionService streamSessionService) {
        this.streamSessionService = streamSessionService;
    }

    @PostMapping("/preview/start")
    public ApiResponse<Map<String, Object>> start(@Valid @RequestBody PreviewStartRequest request) {
        Map<String, Object> data = streamSessionService.startPreview(
                request.getDeviceId(), request.getStreamType());
        return ApiResponse.ok(data);
    }

    @PostMapping("/preview/stop")
    public ApiResponse<Map<String, Object>> stop(@Valid @RequestBody PreviewStopRequest request) {
        Map<String, Object> data = streamSessionService.stopPreview(
                request.getDeviceId(), request.getStreamType());
        return ApiResponse.ok(data);
    }
}

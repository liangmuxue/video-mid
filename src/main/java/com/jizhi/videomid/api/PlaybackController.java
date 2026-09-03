package com.jizhi.videomid.api;

import com.jizhi.videomid.api.dto.ApiResponse;
import com.jizhi.videomid.api.dto.PlaybackClipRequest;
import com.jizhi.videomid.session.StreamSessionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * POST /playback/clip — 按时间段回放。
 */
@RestController
public class PlaybackController {

    private final StreamSessionService streamSessionService;

    public PlaybackController(StreamSessionService streamSessionService) {
        this.streamSessionService = streamSessionService;
    }

    @PostMapping("/playback/clip")
    public ApiResponse<Map<String, Object>> clip(@Valid @RequestBody PlaybackClipRequest request) {
        Map<String, Object> data = streamSessionService.startPlayback(
                request.getDeviceId(),
                request.getStartTime(),
                request.getEndTime(),
                request.getStreamType());
        return ApiResponse.ok(data);
    }
}

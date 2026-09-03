package com.jizhi.videomid.api;

import com.jizhi.videomid.api.dto.ApiResponse;
import com.jizhi.videomid.api.dto.PtzMoveRequest;
import com.jizhi.videomid.ptz.PtzService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * POST /ptz/move — 立刻返回 accepted，指令经 Redis 队列 + WebSocket 下发网关。
 */
@RestController
public class PtzController {

    private final PtzService ptzService;

    public PtzController(PtzService ptzService) {
        this.ptzService = ptzService;
    }

    @PostMapping("/ptz/move")
    public ApiResponse<Map<String, Object>> move(@Valid @RequestBody PtzMoveRequest request) {
        Map<String, Object> data = ptzService.move(
                request.getDeviceId(),
                request.getCmd(),
                request.getSpeed(),
                request.getTimeoutMs());
        return ApiResponse.accepted(data);
    }
}

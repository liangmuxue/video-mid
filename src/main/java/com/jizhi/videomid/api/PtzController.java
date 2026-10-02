package com.jizhi.videomid.api;

import com.jizhi.videomid.auth.dto.ApiResponse;
import com.jizhi.videomid.uniview.dto.PtzActionRequest;
import com.jizhi.videomid.uniview.dto.PtzDeviceIdRequest;
import com.jizhi.videomid.uniview.dto.PtzMoveRequest;
import com.jizhi.videomid.uniview.dto.PtzPresetGotoRequest;
import com.jizhi.videomid.uniview.dto.PtzPresetSaveRequest;
import com.jizhi.videomid.uniview.dto.PtzSnapshotRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 云台入口不区分平台。只传设备编号，服务按该设备的 vendor 调用对应平台。
 */
@RestController
@RequestMapping("/api/ptz")
public class PtzController {

    private final PtzActions actions;

    public PtzController(PtzActions actions) {
        this.actions = actions;
    }

    @GetMapping("/devices")
    public ApiResponse<List<Map<String, Object>>> devices() {
        return ApiResponse.ok(actions.devices());
    }

    @GetMapping("/devices/{deviceId}")
    public ApiResponse<Map<String, Object>> device(@PathVariable String deviceId) {
        return ApiResponse.ok(actions.device(deviceId));
    }

    @PostMapping("/move")
    public ApiResponse<Map<String, Object>> move(@RequestBody PtzMoveRequest req) {
        return ApiResponse.ok(actions.move(req));
    }

    @PostMapping("/zoom")
    public ApiResponse<Map<String, Object>> zoom(@RequestBody PtzActionRequest req) {
        return ApiResponse.ok(actions.zoom(req));
    }

    @PostMapping("/focus")
    public ApiResponse<Map<String, Object>> focus(@RequestBody PtzActionRequest req) {
        return ApiResponse.ok(actions.focus(req));
    }

    @PostMapping("/wide-angle")
    public ApiResponse<Map<String, Object>> wideAngle(@RequestBody PtzDeviceIdRequest req) {
        return ApiResponse.ok(actions.wideAngle(req));
    }

    @PostMapping("/preset/goto")
    public ApiResponse<Map<String, Object>> gotoPreset(@RequestBody PtzPresetGotoRequest req) {
        return ApiResponse.ok(actions.gotoPreset(req));
    }

    @PostMapping("/preset/save")
    public ApiResponse<Map<String, Object>> setPreset(@RequestBody PtzPresetSaveRequest req) {
        return ApiResponse.ok(actions.setPreset(req));
    }

    @PostMapping("/snapshot")
    public ApiResponse<Map<String, Object>> snapshot(@RequestBody PtzSnapshotRequest req) {
        return ApiResponse.ok(actions.snapshot(req));
    }
}

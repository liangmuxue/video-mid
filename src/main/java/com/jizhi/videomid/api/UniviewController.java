package com.jizhi.videomid.api;

import com.jizhi.videomid.auth.dto.ApiResponse;
import com.jizhi.videomid.config.UniviewProperties;
import com.jizhi.videomid.uniview.dto.PtzActionRequest;
import com.jizhi.videomid.uniview.dto.PtzDeviceIdRequest;
import com.jizhi.videomid.uniview.dto.PtzMoveRequest;
import com.jizhi.videomid.uniview.dto.PtzPresetGotoRequest;
import com.jizhi.videomid.uniview.dto.PtzPresetSaveRequest;
import com.jizhi.videomid.uniview.dto.PtzSnapshotRequest;
import com.jizhi.videomid.uniview.live.UniviewLapiClient;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 保留的宇视路径。云台动作与 /api/ptz 相同，仍只按设备编号分发。
 */
@RestController
@RequestMapping("/api/uniview")
public class UniviewController {

    private final UniviewProperties props;
    private final UniviewLapiClient lapiClient;
    private final PtzActions ptzActions;

    public UniviewController(UniviewProperties props,
                             UniviewLapiClient lapiClient,
                             PtzActions ptzActions) {
        this.props = props;
        this.lapiClient = lapiClient;
        this.ptzActions = ptzActions;
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dataSource", props.isMock() ? "mock" : "live");
        m.put("mock", props.isMock());
        m.put("description", props.isMock()
                ? "当前为模拟数据，可独立开发 PTZ/国标页面；联调后改 uniview.data-source=live"
                : "当前为真实宇视数据模式");
        return ApiResponse.ok(m);
    }

    @GetMapping("/live/readiness")
    public ApiResponse<Map<String, Object>> liveReadiness() {
        Map<String, Object> m = new LinkedHashMap<>(lapiClient.readiness());
        m.put("dataSource", props.getDataSource());
        m.put("note", props.isMock()
                ? "当前 mock 模式，live 联调请改 uniview.data-source=live"
                : (lapiClient.isConfigured()
                ? "LAPI 已配置"
                : "请配置 uniview.live.device-host"));
        return ApiResponse.ok(m);
    }

    @GetMapping("/devices")
    public ApiResponse<List<Map<String, Object>>> devices() {
        return ApiResponse.ok(ptzActions.catalogDevices());
    }

    @GetMapping("/devices/{deviceId}")
    public ApiResponse<Map<String, Object>> device(@PathVariable String deviceId) {
        return ApiResponse.ok(ptzActions.device(deviceId));
    }

    @GetMapping("/ptz/devices")
    public ApiResponse<List<Map<String, Object>>> ptzDevices() {
        return ApiResponse.ok(ptzActions.devices());
    }

    @PostMapping("/ptz/move")
    public ApiResponse<Map<String, Object>> move(@RequestBody PtzMoveRequest req) {
        return ApiResponse.ok(ptzActions.move(req));
    }

    @PostMapping("/ptz/zoom")
    public ApiResponse<Map<String, Object>> zoom(@RequestBody PtzActionRequest req) {
        return ApiResponse.ok(ptzActions.zoom(req));
    }

    @PostMapping("/ptz/focus")
    public ApiResponse<Map<String, Object>> focus(@RequestBody PtzActionRequest req) {
        return ApiResponse.ok(ptzActions.focus(req));
    }

    @PostMapping("/ptz/wide-angle")
    public ApiResponse<Map<String, Object>> wideAngle(@RequestBody PtzDeviceIdRequest req) {
        return ApiResponse.ok(ptzActions.wideAngle(req));
    }

    @PostMapping("/ptz/preset/goto")
    public ApiResponse<Map<String, Object>> gotoPreset(@RequestBody PtzPresetGotoRequest req) {
        return ApiResponse.ok(ptzActions.gotoPreset(req));
    }

    @PostMapping("/ptz/preset/save")
    public ApiResponse<Map<String, Object>> setPreset(@RequestBody PtzPresetSaveRequest req) {
        return ApiResponse.ok(ptzActions.setPreset(req));
    }

    @PostMapping("/ptz/snapshot")
    public ApiResponse<Map<String, Object>> snapshot(@RequestBody PtzSnapshotRequest req) {
        return ApiResponse.ok(ptzActions.snapshot(req));
    }
}

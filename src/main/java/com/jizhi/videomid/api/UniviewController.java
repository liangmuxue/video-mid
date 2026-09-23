package com.jizhi.videomid.api;

import com.jizhi.videomid.auth.dto.ApiResponse;
import com.jizhi.videomid.config.UniviewProperties;
import com.jizhi.videomid.uniview.UniviewCatalogPort;
import com.jizhi.videomid.uniview.UniviewPtzPort;
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
 * 宇视对接统一入口。前端/业务只调此处，内部由 mock/live 适配器切换。
 */
@RestController
@RequestMapping("/api/uniview")
public class UniviewController {

    private static final int DEFAULT_SPEED = 4;

    private final UniviewProperties props;
    private final UniviewPtzPort ptzPort;
    private final UniviewCatalogPort catalogPort;
    private final UniviewLapiClient lapiClient;

    public UniviewController(UniviewProperties props,
                             UniviewPtzPort ptzPort,
                             UniviewCatalogPort catalogPort,
                             UniviewLapiClient lapiClient) {
        this.props = props;
        this.ptzPort = ptzPort;
        this.catalogPort = catalogPort;
        this.lapiClient = lapiClient;
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
        return ApiResponse.ok(catalogPort.listDevices());
    }

    @GetMapping("/devices/{deviceId}")
    public ApiResponse<Map<String, Object>> device(@PathVariable String deviceId) {
        return ApiResponse.ok(catalogPort.getDevice(deviceId));
    }

    @GetMapping("/ptz/devices")
    public ApiResponse<List<Map<String, Object>>> ptzDevices() {
        return ApiResponse.ok(ptzPort.listPtzDevices());
    }

    @PostMapping("/ptz/move")
    public ApiResponse<Map<String, Object>> move(@RequestBody PtzMoveRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        return ApiResponse.ok(ptzPort.move(requireDeviceId(req.getDeviceId()),
                requireText(req.getDirection(), "direction"), speedOrDefault(req.getSpeed())));
    }

    @PostMapping("/ptz/zoom")
    public ApiResponse<Map<String, Object>> zoom(@RequestBody PtzActionRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        return ApiResponse.ok(ptzPort.zoom(requireDeviceId(req.getDeviceId()),
                requireText(req.getAction(), "action"), speedOrDefault(req.getSpeed())));
    }

    @PostMapping("/ptz/focus")
    public ApiResponse<Map<String, Object>> focus(@RequestBody PtzActionRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        return ApiResponse.ok(ptzPort.focus(requireDeviceId(req.getDeviceId()),
                requireText(req.getAction(), "action"), speedOrDefault(req.getSpeed())));
    }

    @PostMapping("/ptz/wide-angle")
    public ApiResponse<Map<String, Object>> wideAngle(@RequestBody PtzDeviceIdRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        return ApiResponse.ok(ptzPort.wideAngle(requireDeviceId(req.getDeviceId())));
    }

    @PostMapping("/ptz/preset/goto")
    public ApiResponse<Map<String, Object>> gotoPreset(@RequestBody PtzPresetGotoRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (req.getIndex() == null) {
            throw new IllegalArgumentException("index 不能为空");
        }
        return ApiResponse.ok(ptzPort.gotoPreset(requireDeviceId(req.getDeviceId()), req.getIndex()));
    }

    @PostMapping("/ptz/preset/save")
    public ApiResponse<Map<String, Object>> setPreset(@RequestBody PtzPresetSaveRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (req.getIndex() == null) {
            throw new IllegalArgumentException("index 不能为空");
        }
        boolean overwrite = Boolean.TRUE.equals(req.getOverwrite());
        return ApiResponse.ok(ptzPort.setPreset(requireDeviceId(req.getDeviceId()),
                req.getIndex(), requireText(req.getName(), "name"), overwrite));
    }

    @PostMapping("/ptz/snapshot")
    public ApiResponse<Map<String, Object>> snapshot(@RequestBody PtzSnapshotRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String channelType = req.getChannelType() == null || req.getChannelType().isBlank()
                ? "visible" : req.getChannelType().trim();
        return ApiResponse.ok(ptzPort.snapshot(requireDeviceId(req.getDeviceId()), channelType));
    }

    private static String requireDeviceId(String deviceId) {
        return requireText(deviceId, "deviceId");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }

    private static int speedOrDefault(Integer speed) {
        return speed == null ? DEFAULT_SPEED : speed;
    }
}

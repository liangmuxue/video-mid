package com.jizhi.videomid.api;

import com.jizhi.videomid.auth.dto.ApiResponse;
import com.jizhi.videomid.config.UniviewProperties;
import com.jizhi.videomid.uniview.UniviewCatalogPort;
import com.jizhi.videomid.uniview.UniviewPtzPort;
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
                ? "LAPI 已配置，待实现通道/PTZ 解析"
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

    @PostMapping("/ptz/{deviceId}/move")
    public ApiResponse<Map<String, Object>> move(@PathVariable String deviceId,
                                                 @RequestParam String direction,
                                                 @RequestParam(defaultValue = "4") int speed) {
        return ApiResponse.ok(ptzPort.move(deviceId, direction, speed));
    }

    @PostMapping("/ptz/{deviceId}/zoom")
    public ApiResponse<Map<String, Object>> zoom(@PathVariable String deviceId,
                                                 @RequestParam String action,
                                                 @RequestParam(defaultValue = "4") int speed) {
        return ApiResponse.ok(ptzPort.zoom(deviceId, action, speed));
    }

    @PostMapping("/ptz/{deviceId}/focus")
    public ApiResponse<Map<String, Object>> focus(@PathVariable String deviceId,
                                                  @RequestParam String action,
                                                  @RequestParam(defaultValue = "4") int speed) {
        return ApiResponse.ok(ptzPort.focus(deviceId, action, speed));
    }

    @PostMapping("/ptz/{deviceId}/wide-angle")
    public ApiResponse<Map<String, Object>> wideAngle(@PathVariable String deviceId) {
        return ApiResponse.ok(ptzPort.wideAngle(deviceId));
    }

    @PostMapping("/ptz/{deviceId}/preset/{index}/goto")
    public ApiResponse<Map<String, Object>> gotoPreset(@PathVariable String deviceId,
                                                       @PathVariable int index) {
        return ApiResponse.ok(ptzPort.gotoPreset(deviceId, index));
    }

    @PostMapping("/ptz/{deviceId}/preset/{index}")
    public ApiResponse<Map<String, Object>> setPreset(@PathVariable String deviceId,
                                                      @PathVariable int index,
                                                      @RequestParam String name,
                                                      @RequestParam(defaultValue = "false") boolean overwrite,
                                                      @RequestParam(required = false) Double zoom) {
        return ApiResponse.ok(ptzPort.setPreset(deviceId, index, name, overwrite, zoom));
    }

    @PostMapping("/ptz/{deviceId}/snapshot")
    public ApiResponse<Map<String, Object>> snapshot(@PathVariable String deviceId,
                                                     @RequestParam(defaultValue = "visible") String channelType) {
        return ApiResponse.ok(ptzPort.snapshot(deviceId, channelType));
    }
}

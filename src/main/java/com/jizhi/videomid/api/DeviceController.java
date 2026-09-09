package com.jizhi.videomid.api;

import com.jizhi.videomid.auth.dto.ApiResponse;
import com.jizhi.videomid.device.DeviceService;
import com.jizhi.videomid.device.dto.DeviceRequest;
import com.jizhi.videomid.device.dto.PreviewRequest;
import com.jizhi.videomid.device.dto.StreamRegisterRequest;
import com.jizhi.videomid.session.PreviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DeviceController {

    private final DeviceService deviceService;
    private final PreviewService previewService;

    public DeviceController(DeviceService deviceService, PreviewService previewService) {
        this.deviceService = deviceService;
        this.previewService = previewService;
    }

    @GetMapping("/devices")
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(deviceService.listDevices());
    }

    @GetMapping("/devices/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(deviceService.getDevice(id));
    }

    @PostMapping("/devices")
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody DeviceRequest request) {
        return ApiResponse.ok(deviceService.createDevice(request));
    }

    @PutMapping("/devices/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id,
                                                   @Valid @RequestBody DeviceRequest request) {
        return ApiResponse.ok(deviceService.updateDevice(id, request));
    }

    @DeleteMapping("/devices/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        deviceService.deleteDevice(id);
        return ApiResponse.ok(null);
    }

    /** 码流注册：写入 stream_url（无需国标通道取流） */
    @PostMapping("/streams/register")
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody StreamRegisterRequest request) {
        return ApiResponse.ok(deviceService.registerStream(request));
    }

    @DeleteMapping("/streams/{id}")
    public ApiResponse<Void> deleteStream(@PathVariable Long id) {
        deviceService.deleteStream(id);
        return ApiResponse.ok(null);
    }

    /** 开始预览：返回已注册地址，Redis 播放数 +1 */
    @PostMapping("/preview/start")
    public ApiResponse<Map<String, Object>> previewStart(@Valid @RequestBody PreviewRequest request) {
        return ApiResponse.ok(previewService.start(request.getDeviceId(), request.getStreamType()));
    }

    /** 停止预览：Redis 播放数 -1 */
    @PostMapping("/preview/stop")
    public ApiResponse<Map<String, Object>> previewStop(@Valid @RequestBody PreviewRequest request) {
        return ApiResponse.ok(previewService.stop(request.getDeviceId(), request.getStreamType()));
    }
}

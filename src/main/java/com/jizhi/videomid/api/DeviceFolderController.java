package com.jizhi.videomid.api;

import com.jizhi.videomid.auth.dto.ApiResponse;
import com.jizhi.videomid.device.DeviceFolderService;
import com.jizhi.videomid.device.dto.DeviceFolderRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/device-folders")
public class DeviceFolderController {

    private final DeviceFolderService folderService;

    public DeviceFolderController(DeviceFolderService folderService) {
        this.folderService = folderService;
    }

    @GetMapping("/tree")
    public ApiResponse<List<Map<String, Object>>> tree() {
        return ApiResponse.ok(folderService.tree());
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody DeviceFolderRequest request) {
        return ApiResponse.ok(folderService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id,
                                                   @RequestBody DeviceFolderRequest request) {
        return ApiResponse.ok(folderService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        folderService.delete(id);
        return ApiResponse.ok(null);
    }
}

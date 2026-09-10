package com.jizhi.videomid.api;

import com.jizhi.videomid.auth.dto.ApiResponse;
import com.jizhi.videomid.record.RecordFileService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recordings")
public class RecordFileController {

    private final RecordFileService recordFileService;

    public RecordFileController(RecordFileService recordFileService) {
        this.recordFileService = recordFileService;
    }

    /**
     * 查询设备录制视频。
     * from / to 为时间戳筛选，可选；格式：yyyyMMdd_HHmmss 或 yyyy-MM-dd HH:mm:ss
     */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestParam String deviceId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ApiResponse.ok(recordFileService.list(deviceId, from, to));
    }

    /** 下载 / 播放某个录像文件 */
    @GetMapping("/{deviceId}/{fileName}")
    public ResponseEntity<Resource> file(@PathVariable String deviceId,
                                         @PathVariable String fileName) {
        Resource resource = recordFileService.openFile(deviceId, fileName);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(resource);
    }
}

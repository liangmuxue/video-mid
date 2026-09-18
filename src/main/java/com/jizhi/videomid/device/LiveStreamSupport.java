package com.jizhi.videomid.device;

import java.util.List;
import java.util.Optional;

/** 业务端默认直播码流解析（兼容 main/sub 与 visible_sub 等命名） */
public final class LiveStreamSupport {

    public static final List<String> PRIORITY = List.of(
            "visible_sub", "sub", "visible_main", "main", "thermal_main", "thermal_sub"
    );

    private LiveStreamSupport() {
    }

    public static Optional<DeviceStream> resolveMarked(DeviceStreamRepository repo, String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return Optional.empty();
        }
        return repo.findLiveByDeviceId(deviceId.trim());
    }

    public static Optional<DeviceStream> resolveByPriority(DeviceStreamRepository repo, String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return Optional.empty();
        }
        String id = deviceId.trim();
        Optional<DeviceStream> marked = repo.findLiveByDeviceId(id);
        if (marked.isPresent()) {
            return marked;
        }
        for (String type : PRIORITY) {
            Optional<DeviceStream> s = repo.findByDeviceIdAndType(id, type);
            if (s.isPresent()) {
                return s;
            }
        }
        return Optional.empty();
    }
}

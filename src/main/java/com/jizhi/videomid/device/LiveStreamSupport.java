package com.jizhi.videomid.device;

import java.util.List;
import java.util.Optional;

/** 业务端默认直播码流解析（兼容 main/sub 与 visible_sub 等命名） */
public final class LiveStreamSupport {

    public static final List<String> PRIORITY = List.of(
            "visible_sub", "sub", "visible_main", "main", "third", "thermal_main", "thermal_sub"
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

    /** 从已加载的码流里选业务直播流：已标记优先，否则按辅码流到主码流的顺序。 */
    public static Optional<DeviceStream> pick(List<DeviceStream> streams) {
        if (streams == null || streams.isEmpty()) {
            return Optional.empty();
        }
        for (DeviceStream stream : streams) {
            if (Boolean.TRUE.equals(stream.getLiveEnabled())) {
                return Optional.of(stream);
            }
        }
        for (String type : PRIORITY) {
            for (DeviceStream stream : streams) {
                if (type.equals(stream.getStreamType())) {
                    return Optional.of(stream);
                }
            }
        }
        return Optional.empty();
    }
}

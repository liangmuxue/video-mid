package com.jizhi.videomid.device;

import com.jizhi.videomid.session.PreviewService;
import com.jizhi.videomid.uniview.live.UniviewStreamIds;
import com.jizhi.videomid.uniview.live.UniviewStreamKeeper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

/** 业务直播 {@code liveStream.streamUrl} 统一返回 RTSP 地址。 */
@Component
public class LiveStreamRtspResolver {

    private static final int DEFAULT_RTSP_PORT = 554;

    private final DeviceRepository deviceRepository;
    private final UniviewStreamKeeper univiewStreamKeeper;

    public LiveStreamRtspResolver(DeviceRepository deviceRepository,
                                  UniviewStreamKeeper univiewStreamKeeper) {
        this.deviceRepository = deviceRepository;
        this.univiewStreamKeeper = univiewStreamKeeper;
    }

    public String resolve(String deviceId, DeviceStream stream) {
        if (stream == null) {
            return null;
        }
        String stored = stream.getStreamUrl();
        if (isRtsp(stored)) {
            return stored.trim();
        }
        Optional<Device> deviceOpt = deviceId == null ? Optional.empty() : deviceRepository.findByDeviceId(deviceId);
        if (deviceOpt.isPresent()) {
            Device device = deviceOpt.get();
            if (VendorDevices.of(device) == AccessVendor.UNIVIEW && UniviewStreamIds.isPull(stream)) {
                Optional<String> cameraRtsp = univiewStreamKeeper.resolveRtspLiveUrl(device, stream);
                if (cameraRtsp.isPresent()) {
                    return cameraRtsp.get();
                }
            }
        }
        String zlmRtsp = zlmProxyRtsp(stream, stored);
        if (zlmRtsp != null) {
            return zlmRtsp;
        }
        return isRtsp(stored) ? stored.trim() : null;
    }

    static boolean isRtsp(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String lower = url.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("rtsp://") || lower.startsWith("rtsps://");
    }

    static String zlmProxyRtsp(DeviceStream stream, String httpPlayUrl) {
        String app = blank(stream.getZlmApp());
        String name = blank(stream.getZlmStream());
        if (app == null || name == null) {
            PreviewService.AppStream parsed = PreviewService.parseAppStream(httpPlayUrl);
            if (parsed == null) {
                return null;
            }
            app = parsed.app();
            name = parsed.stream();
        }
        String host = hostFromPlayUrl(httpPlayUrl);
        if (host == null) {
            return null;
        }
        return "rtsp://" + host + ":" + DEFAULT_RTSP_PORT + "/" + app + "/" + name;
    }

    private static String hostFromPlayUrl(String playUrl) {
        if (playUrl == null || playUrl.isBlank()) {
            return null;
        }
        try {
            URI u = URI.create(playUrl.trim());
            if (u.getHost() == null || u.getHost().isBlank()) {
                return null;
            }
            return u.getHost();
        } catch (Exception e) {
            return null;
        }
    }

    private static String blank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

package com.jizhi.videomid.eagleeye;

import com.jizhi.videomid.config.ZlmProperties;
import com.jizhi.videomid.media.ZlmClient;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 鹰眼实况播放已经推到流媒体上的流。约定推流地址为 rtsp://主机:端口/live/{终端编号}。
 */
@Service
public class EagleEyePlayService {

    static final String APP = "live";

    private final ZlmClient zlmClient;
    private final ZlmProperties zlmProperties;
    private final EagleEyeProperties eagleEyeProperties;

    public EagleEyePlayService(ZlmClient zlmClient,
                               ZlmProperties zlmProperties,
                               EagleEyeProperties eagleEyeProperties) {
        this.zlmClient = zlmClient;
        this.zlmProperties = zlmProperties;
        this.eagleEyeProperties = eagleEyeProperties;
    }

    public Map<String, Object> startLive(String deviceId) {
        String stream = eagleEyeProperties.terminalIdOf(deviceId);
        if (stream == null || stream.isBlank()) {
            throw new IllegalArgumentException("鹰眼终端编号为空");
        }
        stream = stream.trim();
        Optional<Boolean> online = zlmClient.isMediaOnline(APP, stream);
        if (online.isPresent() && !online.get()) {
            throw new IllegalArgumentException("鹰眼还没有推流到 live/" + stream);
        }
        String playUrl = httpPlayUrl(deviceId, stream);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deviceId", deviceId);
        data.put("streamType", "main");
        data.put("streamUrl", playUrl);
        data.put("playUrl", playUrl);
        data.put("liveEnabled", true);
        data.put("transport", "eagle-eye");
        return data;
    }

    /** 勾选播放给网页的地址，与宇视云一样是 HTTP-FLV。单台配了内网前缀时只用这一台。 */
    private String httpPlayUrl(String deviceId, String stream) {
        String base = eagleEyeProperties.playBaseUrlOf(deviceId);
        if (base.isBlank()) {
            base = zlmProperties.getPlayBaseUrl();
        }
        return join(base, stream + ".live.flv");
    }

    /** 设备列表里的直播地址，与宇视云一样是 RTSP。单台配了内网前缀时只用这一台。 */
    public String listedPlayUrl(String deviceId) {
        String stream = eagleEyeProperties.terminalIdOf(deviceId);
        if (stream == null || stream.isBlank()) {
            return null;
        }
        String base = eagleEyeProperties.rtspBaseUrlOf(deviceId);
        if (base.isBlank()) {
            base = zlmProperties.getRtspBaseUrl();
        }
        return join(base, stream.trim());
    }

    private static String join(String base, String name) {
        if (base == null || base.isBlank()) {
            throw new IllegalArgumentException("未配置播放地址");
        }
        String prefix = base.trim();
        while (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix + "/" + APP + "/" + name;
    }
}

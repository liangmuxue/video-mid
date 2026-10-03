package com.jizhi.videomid.uniview.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.jizhi.videomid.config.UniviewProperties;
import com.jizhi.videomid.config.ZlmProperties;
import com.jizhi.videomid.media.ZlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把宇视 LiveStreamURL 交给 ZLM 拉流，返回播放器可用的 HTTP-FLV。
 */
@Service
public class UniviewLivePreviewService {

    private static final Logger log = LoggerFactory.getLogger(UniviewLivePreviewService.class);
    private static final String APP = "live";

    private final UniviewLapiClient lapiClient;
    private final UniviewProperties univiewProperties;
    private final ZlmClient zlmClient;
    private final ZlmProperties zlmProperties;

    public UniviewLivePreviewService(UniviewLapiClient lapiClient,
                                     UniviewProperties univiewProperties,
                                     ZlmClient zlmClient,
                                     ZlmProperties zlmProperties) {
        this.lapiClient = lapiClient;
        this.univiewProperties = univiewProperties;
        this.zlmClient = zlmClient;
        this.zlmProperties = zlmProperties;
    }

    public List<Map<String, Object>> playableStreams(String channelId) {
        String channel = channelId == null || channelId.isBlank() ? "0" : channelId;
        List<Map<String, Object>> streams = new ArrayList<>();
        addStream(streams, channel, 0, "main", "可见光-主码流");
        addStream(streams, channel, 1, "sub", "可见光-子码流");
        return streams;
    }

    private void addStream(List<Map<String, Object>> streams, String channelId, int streamIndex,
                           String streamType, String streamName) {
        String rtsp = liveStreamUrl(channelId, streamIndex);
        if (rtsp == null || rtsp.isBlank()) {
            return;
        }
        String source = withCredentials(rtsp);
        String stream = streamKey(streamType);
        if (!zlmClient.addStreamProxy(APP, stream, source)) {
            log.warn("[Uniview-LAPI] ZLM 拉流未成功 channel={} stream={}", channelId, stream);
            return;
        }
        waitUntilOnline(stream);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("channelId", channelId);
        item.put("channelType", "visible");
        item.put("streamType", streamType);
        item.put("streamName", streamName);
        item.put("streamUrl", playUrl(stream));
        item.put("status", "ON");
        streams.add(item);
    }

    private String liveStreamUrl(String channelId, int streamIndex) {
        String path = "/LAPI/V1.0/Channels/" + channelId + "/Media/Video/Streams/" + streamIndex + "/LiveStreamURL";
        try {
            JsonNode root = lapiClient.get(path);
            int code = root.path("Response").path("ResponseCode").asInt(-1);
            if (code != 0) {
                log.warn("[Uniview-LAPI] LiveStreamURL 失败 path={} code={}", path, code);
                return null;
            }
            String url = root.path("Response").path("Data").path("URL").asText("");
            return url.isBlank() ? null : url;
        } catch (RuntimeException e) {
            log.warn("[Uniview-LAPI] LiveStreamURL 失败 path={} error={}", path, e.getMessage());
            return null;
        }
    }

    private String withCredentials(String rtspUrl) {
        if (rtspUrl.contains("@")) {
            return rtspUrl;
        }
        String username = univiewProperties.getLive().getUsername() == null ? "" : univiewProperties.getLive().getUsername();
        String password = univiewProperties.getLive().getPassword() == null ? "" : univiewProperties.getLive().getPassword();
        String rest = rtspUrl.startsWith("rtsp://") ? rtspUrl.substring("rtsp://".length()) : rtspUrl;
        return "rtsp://" + username + ":" + password + "@" + rest;
    }

    private String streamKey(String streamType) {
        String host = univiewProperties.getLive().getDeviceHost();
        String safeHost = host == null ? "device" : host.trim().replace('.', '_').replace(':', '_');
        return "uniview_" + safeHost + "_" + streamType;
    }

    private String playUrl(String stream) {
        String base = zlmProperties.getPlayBaseUrl();
        if (base == null || base.isBlank()) {
            base = "http://127.0.0.1:8080";
        }
        base = base.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + APP + "/" + stream + ".live.flv";
    }

    private void waitUntilOnline(String stream) {
        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline) {
            if (Boolean.TRUE.equals(zlmClient.isMediaOnline(APP, stream).orElse(false))) {
                return;
            }
            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.info("[Uniview-LAPI] ZLM 流尚未就绪，仍返回播放地址 stream={}", stream);
    }
}

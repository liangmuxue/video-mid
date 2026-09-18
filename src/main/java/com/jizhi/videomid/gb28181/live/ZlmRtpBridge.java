package com.jizhi.videomid.gb28181.live;

import com.jizhi.videomid.config.Gb28181Properties;
import com.jizhi.videomid.config.ZlmProperties;
import com.jizhi.videomid.media.ZlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** live INVITE：在 ZLM 开 RTP 接收端口并生成播放地址 */
@Component
public class ZlmRtpBridge {

    private static final Logger log = LoggerFactory.getLogger(ZlmRtpBridge.class);

    private final Gb28181Properties gbProps;
    private final ZlmProperties zlmProps;
    private final ZlmClient zlmClient;
    private final RtpPortAllocator portAllocator;
    private final AtomicInteger ssrcSeq = new AtomicInteger(0x20000001);
    private final ConcurrentHashMap<String, Integer> channelPorts = new ConcurrentHashMap<>();

    public ZlmRtpBridge(Gb28181Properties gbProps,
                        ZlmProperties zlmProps,
                        ZlmClient zlmClient,
                        RtpPortAllocator portAllocator) {
        this.gbProps = gbProps;
        this.zlmProps = zlmProps;
        this.zlmClient = zlmClient;
        this.portAllocator = portAllocator;
    }

    public Map<String, Object> openReceive(String channelId, String fallbackPlayUrl) {
        closeReceive(channelId);
        String streamId = streamIdFor(channelId);
        String app = gbProps.getLive().getZlmApp();
        int rtpPort = portAllocator.allocate();
        channelPorts.put(channelId.trim(), rtpPort);
        int ssrc = ssrcSeq.getAndIncrement();
        String ssrcHex = String.format("%08X", ssrc);

        boolean zlmOk = false;
        try {
            zlmOk = zlmClient.openRtpServer(rtpPort, streamId, app);
        } catch (Exception e) {
            log.warn("[GB28181-live] openRtpServer 失败 channelId={} port={} err={}", channelId, rtpPort, e.getMessage());
        }

        String rtpPlayUrl = buildPlayUrl(app, streamId);
        String playUrl = rtpPlayUrl;
        String playSource = "zlm-rtp";
        if (gbProps.getLive().isFallbackToDbStream()
                && fallbackPlayUrl != null && !fallbackPlayUrl.isBlank()) {
            playUrl = fallbackPlayUrl.trim();
            playSource = "db-stream-fallback";
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mock", false);
        m.put("live", true);
        m.put("channelId", channelId);
        m.put("ssrc", ssrcHex);
        m.put("transport", gbProps.getMedia().getTransport());
        m.put("rtpPort", rtpPort);
        m.put("zlmApp", app);
        m.put("streamId", streamId);
        m.put("zlmRtpReady", zlmOk);
        m.put("signaling", "SIP INVITE");
        m.put("media", "RTP/PS → ZLM");
        m.put("rtpPlayUrl", rtpPlayUrl);
        m.put("playUrl", playUrl);
        m.put("playSource", playSource);
        m.put("note", zlmOk
                ? "RTP 端口已开；下级推流后可用 rtpPlayUrl 播放"
                : "ZLM openRtpServer 失败，当前使用 DB 演示流地址");
        return m;
    }

    public void closeReceive(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return;
        }
        String id = channelId.trim();
        Integer port = channelPorts.remove(id);
        if (port != null) {
            portAllocator.release(port);
        }
        String streamId = streamIdFor(id);
        String app = gbProps.getLive().getZlmApp();
        try {
            zlmClient.closeRtpServer(streamId, app);
        } catch (Exception e) {
            log.debug("[GB28181-live] closeRtpServer channelId={} err={}", id, e.getMessage());
        }
    }

    private String streamIdFor(String channelId) {
        return "gb_" + channelId.trim();
    }

    private String buildPlayUrl(String app, String streamId) {
        String base = zlmProps.getBaseUrl();
        if (base == null || base.isBlank()) {
            base = "http://127.0.0.1:8080";
        }
        base = base.replaceAll("/+$", "");
        return base + "/" + app + "/" + streamId + ".live.flv";
    }
}

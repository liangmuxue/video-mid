package com.jizhi.videomid.gb28181.mock;

import com.jizhi.videomid.config.Gb28181Properties;
import com.jizhi.videomid.config.UniviewProperties;
import com.jizhi.videomid.gb28181.Gb28181CatalogPort;
import com.jizhi.videomid.gb28181.Gb28181CatalogXmlBuilder;
import com.jizhi.videomid.uniview.mock.MockUniviewDataLoader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@ConditionalOnProperty(name = "gb28181.data-source", havingValue = "mock", matchIfMissing = true)
public class MockGb28181CatalogAdapter implements Gb28181CatalogPort {

    private final MockUniviewDataLoader dataLoader;
    private final Gb28181Properties gbProps;
    private final UniviewProperties univiewProps;
    private final AtomicInteger ssrcSeq = new AtomicInteger(0x10000001);

    public MockGb28181CatalogAdapter(MockUniviewDataLoader dataLoader,
                                     Gb28181Properties gbProps,
                                     UniviewProperties univiewProps) {
        this.dataLoader = dataLoader;
        this.gbProps = gbProps;
        this.univiewProps = univiewProps;
    }

    @Override
    public List<Map<String, Object>> listCatalog() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> d : dataLoader.devices()) {
            out.add(toCatalogRow(d));
        }
        return out;
    }

    @Override
    public Map<String, Object> getChannel(String channelId) {
        for (Map<String, Object> d : dataLoader.devices()) {
            for (Map<String, Object> ch : channels(d)) {
                if (channelId.equals(String.valueOf(ch.get("channelId")))) {
                    Map<String, Object> m = new HashMap<>(ch);
                    m.put("deviceId", d.get("deviceId"));
                    m.put("gbDeviceId", d.get("gbDeviceId"));
                    m.put("deviceName", d.get("name"));
                    return m;
                }
            }
        }
        throw new IllegalArgumentException("国标通道不存在: " + channelId);
    }

    @Override
    public String buildCatalogXml() {
        return Gb28181CatalogXmlBuilder.build(listCatalog());
    }

    @Override
    public Map<String, Object> invite(String channelId) {
        Map<String, Object> ch = getChannel(channelId);
        String previewKey = String.valueOf(ch.get("previewKey"));
        String base = gbProps.getMock().getPreviewBaseUrl();
        if (base == null || base.isBlank()) {
            base = univiewProps.getMock().getPreviewBaseUrl();
        }
        base = base.replaceAll("/+$", "");
        int ssrc = ssrcSeq.getAndIncrement();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mock", true);
        m.put("channelId", channelId);
        m.put("deviceId", ch.get("deviceId"));
        m.put("ssrc", String.format("%08X", ssrc));
        m.put("transport", gbProps.getMedia().getTransport());
        m.put("rtpPortMin", gbProps.getMedia().getRtpPortMin());
        m.put("rtpPortMax", gbProps.getMedia().getRtpPortMax());
        m.put("signaling", "SIP INVITE (mock)");
        m.put("media", "RTP/PS (mock → 演示 FLV)");
        m.put("playUrl", base + "/" + previewKey + ".live.flv");
        m.put("note", "live 模式将建立真实国标信令与 RTP 媒体链路，不使用 RTSP");
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toCatalogRow(Map<String, Object> d) {
        Map<String, Object> m = new HashMap<>();
        m.put("deviceId", d.get("deviceId"));
        m.put("name", d.get("name"));
        m.put("gbDeviceId", d.get("gbDeviceId"));
        m.put("manufacturer", d.get("manufacturer"));
        m.put("model", d.get("model"));
        m.put("status", "ON");
        m.put("registered", gbProps.getMock().isAutoRegister());
        m.put("mock", true);

        String base = gbProps.getMock().getPreviewBaseUrl().replaceAll("/+$", "");
        List<Map<String, Object>> channels = new ArrayList<>();
        for (Map<String, Object> ch : channels(d)) {
            Map<String, Object> cv = new HashMap<>(ch);
            cv.put("gbStatus", "ON");
            cv.put("playUrl", base + "/" + ch.get("previewKey") + ".live.flv");
            channels.add(cv);
        }
        m.put("channels", channels);
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> channels(Map<String, Object> d) {
        Object raw = d.get("channels");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

}

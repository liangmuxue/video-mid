package com.jizhi.videomid.uniview.mock;

import com.jizhi.videomid.config.UniviewProperties;
import com.jizhi.videomid.uniview.UniviewCatalogPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "uniview.data-source", havingValue = "mock", matchIfMissing = true)
public class MockUniviewCatalogAdapter implements UniviewCatalogPort {

    private final MockUniviewDataLoader dataLoader;
    private final UniviewProperties props;

    public MockUniviewCatalogAdapter(MockUniviewDataLoader dataLoader, UniviewProperties props) {
        this.dataLoader = dataLoader;
        this.props = props;
    }

    @Override
    public List<Map<String, Object>> listDevices() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> d : dataLoader.devices()) {
            out.add(toCatalogView(d));
        }
        return out;
    }

    @Override
    public Map<String, Object> getDevice(String deviceId) {
        return toCatalogView(dataLoader.requireDevice(deviceId));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toCatalogView(Map<String, Object> d) {
        Map<String, Object> m = new HashMap<>();
        m.put("deviceId", d.get("deviceId"));
        m.put("name", d.get("name"));
        m.put("model", d.get("model"));
        m.put("manufacturer", d.get("manufacturer"));
        m.put("platformId", d.get("platformId"));
        m.put("gbDeviceId", d.get("gbDeviceId"));
        m.put("address", d.get("address"));
        m.put("ptzType", d.get("ptzType"));
        m.put("longitude", d.get("longitude"));
        m.put("latitude", d.get("latitude"));
        m.put("status", 1);
        m.put("mock", true);

        String base = props.getMock().getPreviewBaseUrl().replaceAll("/+$", "");
        List<Map<String, Object>> streams = new ArrayList<>();
        Object chRaw = d.get("channels");
        if (chRaw instanceof List<?> chList) {
            for (Object o : chList) {
                if (!(o instanceof Map<?, ?> ch)) continue;
                Map<String, Object> s = new HashMap<>();
                s.put("channelId", ch.get("channelId"));
                s.put("channelType", ch.get("channelType"));
                s.put("streamType", ch.get("streamType"));
                s.put("streamName", ch.get("streamName"));
                String key = String.valueOf(ch.get("previewKey"));
                s.put("streamUrl", base + "/" + key + ".live.flv");
                s.put("status", "ON");
                s.put("mock", true);
                streams.add(s);
            }
        }
        m.put("streams", streams);
        m.put("streamCount", streams.size());
        m.put("presets", d.get("presets"));
        return m;
    }
}

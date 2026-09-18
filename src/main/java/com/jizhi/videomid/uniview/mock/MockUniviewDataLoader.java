package com.jizhi.videomid.uniview.mock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jizhi.videomid.config.UniviewProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class MockUniviewDataLoader {

    private final UniviewProperties props;
    private final ObjectMapper objectMapper;
    private volatile List<Map<String, Object>> cache;

    public MockUniviewDataLoader(UniviewProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> devices() {
        if (cache != null) {
            return cache;
        }
        synchronized (this) {
            if (cache != null) {
                return cache;
            }
            String resource = props.getMock().getDevicesResource();
            try (InputStream in = new ClassPathResource(resource).getInputStream()) {
                List<Map<String, Object>> list = objectMapper.readValue(in, new TypeReference<>() {});
                cache = List.copyOf(list);
                return cache;
            } catch (Exception e) {
                throw new IllegalStateException("加载宇视模拟设备失败: " + resource, e);
            }
        }
    }

    public Map<String, Object> requireDevice(String deviceId) {
        return devices().stream()
                .filter(d -> deviceId.equals(String.valueOf(d.get("deviceId"))))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("模拟设备不存在: " + deviceId));
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> presets(String deviceId) {
        Object raw = requireDevice(deviceId).get("presets");
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    out.add((Map<String, Object>) m);
                }
            }
            return out;
        }
        return List.of();
    }
}

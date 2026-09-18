package com.jizhi.videomid.uniview.mock;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 模拟预置位可增改（内存），live 模式由 SDK 接管 */
@Component
public class MockPresetStore {

    private final MockUniviewDataLoader dataLoader;
    private final ConcurrentHashMap<String, List<Map<String, Object>>> presetsByDevice = new ConcurrentHashMap<>();

    public MockPresetStore(MockUniviewDataLoader dataLoader) {
        this.dataLoader = dataLoader;
    }

    public List<Map<String, Object>> list(String deviceId) {
        return presetsByDevice.computeIfAbsent(deviceId, id -> copyPresets(dataLoader.presets(id)));
    }

    public Map<String, Object> set(String deviceId, int presetIndex, String name, boolean overwrite) {
        List<Map<String, Object>> list = new ArrayList<>(list(deviceId));
        Map<String, Object> existing = null;
        for (Map<String, Object> p : list) {
            if (presetIndex == intVal(p.get("index"))) {
                existing = p;
                break;
            }
        }
        if (existing != null && !overwrite) {
            throw new IllegalArgumentException("预置位 " + presetIndex + " 已存在，请勾选覆盖");
        }
        Map<String, Object> row = new HashMap<>();
        row.put("index", presetIndex);
        row.put("name", name);
        row.put("zoom", existing != null ? existing.get("zoom") : 1.0);
        if (existing != null && existing.get("channelType") != null) {
            row.put("channelType", existing.get("channelType"));
        }
        if (existing != null) {
            list.remove(existing);
        }
        list.add(row);
        list.sort((a, b) -> Integer.compare(intVal(a.get("index")), intVal(b.get("index"))));
        presetsByDevice.put(deviceId, List.copyOf(list));
        return row;
    }

    private static List<Map<String, Object>> copyPresets(List<Map<String, Object>> src) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> p : src) {
            out.add(new HashMap<>(p));
        }
        return out;
    }

    private static int intVal(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(String.valueOf(o));
    }
}

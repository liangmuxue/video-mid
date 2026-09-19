package com.jizhi.videomid.uniview.mock;

import com.jizhi.videomid.device.DevicePtzPresetService;
import com.jizhi.videomid.mock.MockSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 仅 mock：JSON 初始预置位灌进正式目录表（已有行不覆盖）。 */
@Component
@ConditionalOnExpression(MockSupport.FIXTURES_ENABLED)
public class MockPresetCatalogSeeder {

    private final MockUniviewDataLoader dataLoader;
    private final DevicePtzPresetService catalog;

    public MockPresetCatalogSeeder(MockUniviewDataLoader dataLoader, DevicePtzPresetService catalog) {
        this.dataLoader = dataLoader;
        this.catalog = catalog;
    }

    public void ensureSeeded(String deviceId) {
        if (!catalog.listMaps(deviceId).isEmpty()) {
            return;
        }
        for (Map<String, Object> p : dataLoader.presets(deviceId)) {
            int index = toInt(p.get("index"));
            String name = p.get("name") == null ? ("预置位 " + index) : String.valueOf(p.get("name"));
            Double zoom = toDouble(p.get("zoom"));
            catalog.save(deviceId, index, name, zoom, true);
        }
    }

    public void ensureAll() {
        for (Map<String, Object> d : dataLoader.devices()) {
            ensureSeeded(String.valueOf(d.get("deviceId")));
        }
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(String.valueOf(o));
    }

    private static Double toDouble(Object o) {
        if (o == null) {
            return 1.0;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }
}

package com.jizhi.videomid.gb28181.sip.live;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Catalog 多包响应聚合（SumNum 分页） */
@Component
@ConditionalOnProperty(name = "gb28181.data-source", havingValue = "live")
public class Gb28181CatalogBuffer {

    private final ConcurrentHashMap<String, Accumulator> buffers = new ConcurrentHashMap<>();

    public Optional<List<Map<String, String>>> accept(String deviceId, String xml) {
        if (deviceId == null || deviceId.isBlank() || xml == null || xml.isBlank()) {
            return Optional.empty();
        }
        int sumNum = parseInt(Gb28181XmlHelper.tagValue(xml, "SumNum"), -1);
        List<Map<String, String>> batch = Gb28181XmlHelper.parseCatalogItems(xml);
        if (batch.isEmpty() && sumNum <= 0) {
            return Optional.empty();
        }
        Accumulator acc = buffers.computeIfAbsent(deviceId.trim(), k -> new Accumulator(sumNum));
        if (sumNum > 0) {
            acc.sumNum = sumNum;
        }
        acc.items.addAll(batch);
        if (acc.isComplete()) {
            buffers.remove(deviceId.trim());
            return Optional.of(acc.items);
        }
        return Optional.empty();
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static final class Accumulator {
        private int sumNum;
        private final List<Map<String, String>> items = new ArrayList<>();

        Accumulator(int sumNum) {
            this.sumNum = sumNum;
        }

        boolean isComplete() {
            if (sumNum <= 0) {
                return !items.isEmpty();
            }
            return items.size() >= sumNum;
        }
    }
}

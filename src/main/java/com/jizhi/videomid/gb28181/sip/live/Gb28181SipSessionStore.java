package com.jizhi.videomid.gb28181.sip.live;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "gb28181.data-source", havingValue = "live")
public class Gb28181SipSessionStore {

    private final ConcurrentHashMap<String, Gb28181SipSession> byDeviceId = new ConcurrentHashMap<>();

    public void upsert(Gb28181SipSession session) {
        if (session == null || session.getDeviceId() == null) {
            return;
        }
        byDeviceId.put(session.getDeviceId(), session);
    }

    public void remove(String deviceId) {
        if (deviceId != null) {
            byDeviceId.remove(deviceId.trim());
        }
    }

    public Optional<Gb28181SipSession> findByDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return Optional.empty();
        }
        Gb28181SipSession s = byDeviceId.get(deviceId.trim());
        if (s == null || s.isExpired()) {
            if (s != null) {
                byDeviceId.remove(deviceId.trim());
            }
            return Optional.empty();
        }
        return Optional.of(s);
    }

    public int activeCount() {
        purgeExpired();
        return byDeviceId.size();
    }

    public List<Map<String, Object>> listActive() {
        purgeExpired();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Gb28181SipSession s : byDeviceId.values()) {
            if (s.isExpired()) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("deviceId", s.getDeviceId());
            m.put("contactHost", s.getContactHost());
            m.put("contactPort", s.getContactPort());
            m.put("transport", s.getTransport());
            m.put("expiresSeconds", s.getExpiresSeconds());
            m.put("registeredAt", s.getRegisteredAt().toString());
            m.put("expiresAt", s.getExpiresAt().toString());
            m.put("rawContact", s.getRawContact());
            out.add(m);
        }
        return out;
    }

    private void purgeExpired() {
        byDeviceId.entrySet().removeIf(e -> e.getValue().isExpired());
    }
}
